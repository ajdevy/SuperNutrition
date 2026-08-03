#!/usr/bin/env python3
"""Replay suggestion-oriented fusion policies over saved PoC outputs.

This is an E0 policy experiment. It deliberately does not run ONNX models and
does not claim calibrated probabilities. It consumes the existing 100-case
comparison output, keeps raw visual proposals available for every non-hard
policy, and separates visual evidence from composition-only suggestions.
Nutrition5k metadata remains a weak recipe-level proxy, not visible-food truth.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


COMPONENT_LABELS = {"banana", "apple", "orange", "broccoli", "carrot"}
POLICY_VERSION = "e0-suggestion-policy.v1"

# This mirrors PocDishIngredientPriors.java for the host-only policy replay.
# The existing comparison output remains the source for the top-1 prior; this
# map is needed only to derive top-2/top-3 composition support.
PRIORS = {
    "greek_salad": ["cucumber", "tomato", "red_onion", "bell_pepper", "feta_cheese", "olives", "olive_oil"],
    "caesar_salad": ["romaine_lettuce", "chicken", "parmesan", "croutons", "caesar_dressing"],
    "garden_salad": ["lettuce", "tomato", "cucumber", "carrot", "onion", "bell_pepper"],
    "chopped_salad": ["lettuce", "tomato", "cucumber", "carrot", "corn", "onion"],
    "hot_and_sour_soup": ["tofu", "mushroom", "bamboo_shoots", "pork", "egg", "vinegar"],
    "miso_soup": ["miso", "tofu", "seaweed", "green_onion", "mushroom"],
    "ramen": ["noodles", "pork", "egg", "seaweed", "green_onion", "broth"],
    "pizza": ["dough", "tomato_sauce", "mozzarella", "cheese", "pepperoni", "mushroom", "onion"],
    "pork_chop": ["pork_chop", "pork", "carrot", "potato", "green_beans", "apple_sauce"],
    "steak": ["beef", "potato", "green_beans", "salad", "sauce"],
    "grilled_salmon": ["salmon", "lemon", "rice", "broccoli", "asparagus"],
    "sushi": ["rice", "salmon", "tuna", "avocado", "cucumber", "seaweed"],
    "fried_rice": ["rice", "egg", "peas", "carrot", "green_onion"],
    "bibimbap": ["rice", "beef", "egg", "carrot", "spinach", "mushroom"],
    "chicken_curry": ["chicken", "rice", "onion", "tomato", "coconut_milk"],
    "hamburger": ["beef", "bun", "lettuce", "tomato", "onion", "pickle", "cheese"],
    "lasagna": ["pasta", "tomato_sauce", "beef", "cheese", "ricotta"],
    "spaghetti_bolognese": ["pasta", "beef", "tomato_sauce", "parmesan"],
    "waffles": ["flour", "egg", "milk", "butter", "syrup", "berries"],
}


def prior_support(dishes: list[dict[str, Any]], count: int) -> dict[str, float]:
    """Return uncalibrated composition support from the top-k dish outputs."""

    support: dict[str, float] = {}
    for dish in dishes[:count]:
        for ingredient in PRIORS.get(dish["label"], []):
            support[ingredient] = support.get(ingredient, 0.0) + float(dish["confidence"])
    return support


def normalize_support(support: dict[str, float]) -> dict[str, float]:
    maximum = max(support.values(), default=0.0)
    if maximum <= 0.0:
        return {label: 0.0 for label in support}
    return {label: value / maximum for label, value in support.items()}


def component_support(support: dict[str, float]) -> dict[str, float]:
    return {label: value for label, value in normalize_support(support).items() if label in COMPONENT_LABELS}


def sorted_regions(
    regions: list[dict[str, Any]], support: dict[str, float], prior_weight: float
) -> list[dict[str, Any]]:
    normalized = normalize_support(support)
    ranked: list[dict[str, Any]] = []
    for region in regions:
        visual_score = float(region["confidence"])
        composition_score = float(normalized.get(region["label"], 0.0))
        rank_score = (1.0 - prior_weight) * visual_score + prior_weight * composition_score
        ranked.append(
            {
                **region,
                "visual_score": visual_score,
                "composition_support": composition_score,
                "rank_score": rank_score,
                "decision_reason": (
                    "visual proposal reranked with bounded composition support"
                    if composition_score > 0.0
                    else "visual proposal retained; no composition support"
                ),
            }
        )
    return sorted(ranked, key=lambda item: item["rank_score"], reverse=True)


def metric_counts(expected: set[str], predicted: set[str]) -> tuple[int, int, int]:
    return len(expected & predicted), len(predicted - expected), len(expected - predicted)


def aggregate(rows: list[dict[str, Any]], field: str) -> dict[str, Any]:
    tp = fp = fn = 0
    f1_values: list[float] = []
    nonempty = 0
    for row in rows:
        expected = set(row["weak_detector_targets"])
        predicted = set(row[field])
        row_tp, row_fp, row_fn = metric_counts(expected, predicted)
        tp += row_tp
        fp += row_fp
        fn += row_fn
        if expected:
            nonempty += 1
            precision = row_tp / len(predicted) if predicted else 0.0
            recall = row_tp / len(expected)
            f1_values.append(2 * precision * recall / (precision + recall) if precision + recall else 0.0)
    precision = tp / (tp + fp) if tp + fp else 0.0
    recall = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
    return {
        "micro_true_positive": tp,
        "micro_false_positive": fp,
        "micro_false_negative": fn,
        "micro_precision": precision,
        "micro_recall": recall,
        "micro_f1": f1,
        "macro_f1_on_nonempty_weak_target_cases": sum(f1_values) / len(f1_values) if f1_values else None,
        "nonempty_weak_target_cases": nonempty,
    }


def build_strategy(name: str, raw: list[dict[str, Any]], dishes: list[dict[str, Any]]) -> dict[str, Any]:
    top1_support = component_support(prior_support(dishes, 1))
    top3_support = component_support(prior_support(dishes, 3))

    if name == "no_fusion":
        visual_regions = sorted_regions(raw, {}, 0.0)
        composition = {}
    elif name == "hard_top1_dish_prior":
        top1_prior = set(PRIORS.get(dishes[0]["label"], []))
        if not top1_prior:
            visual_regions = sorted_regions(raw, {}, 0.0)
        else:
            visual_regions = [
                {
                    **region,
                    "visual_score": float(region["confidence"]),
                    "composition_support": 1.0 if region["label"] in top1_prior else 0.0,
                    "rank_score": float(region["confidence"]),
                    "decision_reason": "accepted by exact top-1 prior",
                }
                for region in raw
                if region["label"] in top1_prior
            ]
        composition = {}
    elif name == "top1_soft_support":
        visual_regions = sorted_regions(raw, top1_support, 0.20)
        composition = top1_support
    elif name == "topk_union":
        visual_regions = sorted_regions(raw, {}, 0.0)
        composition = top3_support
    elif name == "bounded_late_fusion":
        visual_regions = sorted_regions(raw, top3_support, 0.20)
        composition = top3_support
    else:
        raise ValueError(f"unknown strategy: {name}")

    visual_labels = sorted({region["label"] for region in visual_regions})
    composition_labels = sorted(composition)
    prior_only_labels = sorted(set(composition_labels) - set(visual_labels))
    return {
        "visual_regions": visual_regions,
        "visual_labels": visual_labels,
        "composition_labels": composition_labels,
        "prior_only_labels": prior_only_labels,
        "overlay_region_count": len(visual_regions),
        "decision_provenance": {
            "policy_version": POLICY_VERSION,
            "raw_visual_proposals_retained": name != "hard_top1_dish_prior" or not PRIORS.get(dishes[0]["label"], []),
            "visual_claims_require_region": True,
            "composition_only_never_overlayed": True,
            "calibrated": False,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--comparison", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    comparison = json.loads(args.comparison.read_text(encoding="utf-8"))
    strategy_names = [
        "no_fusion",
        "hard_top1_dish_prior",
        "top1_soft_support",
        "topk_union",
        "bounded_late_fusion",
    ]
    cases: list[dict[str, Any]] = []
    for case in comparison["cases"]:
        raw_regions = case["raw_regions"]
        dishes = case["dish_top3"]
        strategies = {
            name: build_strategy(name, raw_regions, dishes)
            for name in strategy_names
        }
        cases.append(
            {
                "case_id": case["case_id"],
                "dish_id": case["dish_id"],
                "dish_top3": dishes,
                "weak_detector_targets": case["weak_detector_targets"],
                "raw_region_count": len(raw_regions),
                "strategies": strategies,
                "crop_file": case["crop_file"],
            }
        )

    strategies: dict[str, Any] = {}
    for name in strategy_names:
        strategy_rows = []
        total_regions = 0
        prior_only_images = 0
        prior_only_suggestions = 0
        for case in cases:
            output = case["strategies"][name]
            total_regions += output["overlay_region_count"]
            prior_only_suggestions += len(output["prior_only_labels"])
            prior_only_images += bool(output["prior_only_labels"])
            strategy_rows.append(
                {
                    "weak_detector_targets": case["weak_detector_targets"],
                    "visual_labels": output["visual_labels"],
                    "suggestion_labels": sorted(set(output["visual_labels"]) | set(output["composition_labels"])),
                }
            )
        strategies[name] = {
            "description": {
                "no_fusion": "Raw visual proposals, no dish prior.",
                "hard_top1_dish_prior": "Current hard top-1 prior gate; retained for baseline only.",
                "top1_soft_support": "Keep all visual proposals; use top-1 prior only to rank and add separate composition suggestions.",
                "topk_union": "Keep all visual proposals; use the union of top-3 priors as composition-only suggestions.",
                "bounded_late_fusion": "Keep all visual proposals; bounded top-3 composition support affects ranking only.",
            }[name],
            "calibration_status": "not_calibrated_research_replay",
            "total_overlay_regions": total_regions,
            "images_with_overlay_regions": sum(bool(case["strategies"][name]["visual_regions"]) for case in cases),
            "prior_only_suggestion_count": prior_only_suggestions,
            "images_with_prior_only_suggestions": prior_only_images,
            "visual_metrics_weak_proxy": aggregate(strategy_rows, "visual_labels"),
            "visual_plus_composition_metrics_weak_proxy": aggregate(strategy_rows, "suggestion_labels"),
        }

    result = {
        "schema": "supernutrition.nutrition5k-policy-replay.v1",
        "policy_version": POLICY_VERSION,
        "dataset": comparison["dataset"],
        "sample_count": comparison["sample_count"],
        "source_comparison": str(args.comparison),
        "evaluation_warning": (
            "Nutrition5k metadata is recipe-level weak supervision, not visible-region ground truth. "
            "No metric here is a calibrated visual-claim metric."
        ),
        "strategies": strategies,
        "cases": cases,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"replayed {len(cases)} cases across {len(strategy_names)} strategies")
    print(f"wrote {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
