#!/usr/bin/env python3
"""Evaluate ingredient-set and plated-mass predictions against G-3 fixtures.

The evaluator is intentionally dependency-free and accepts predictions from a
future detector/segmenter or mass-estimation engine. The current Food-101
single-label runner has no such fields, so running it without --predictions
produces an explicit not-evaluable baseline rather than a fabricated score.
"""

from __future__ import annotations

import argparse
import json
import math
import statistics
from pathlib import Path
from typing import Any


def _load(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, dict):
        raise ValueError(f"Expected JSON object in {path}")
    return value


def _normalise_labels(values: Any) -> set[str]:
    if not isinstance(values, list) or not all(isinstance(value, str) for value in values):
        raise ValueError("ingredient predictions must be a list of strings")
    return {value.strip().lower() for value in values if value.strip()}


def _f1(expected: set[str], predicted: set[str]) -> tuple[float, float, float]:
    true_positive = len(expected & predicted)
    precision = true_positive / len(predicted) if predicted else 0.0
    recall = true_positive / len(expected) if expected else 1.0
    if precision + recall == 0.0:
        return precision, recall, 0.0
    return precision, recall, 2.0 * precision * recall / (precision + recall)


def evaluate(manifest: dict[str, Any], predictions: dict[str, Any] | None) -> dict[str, Any]:
    cases = manifest.get("cases")
    if not isinstance(cases, list) or not cases:
        raise ValueError("manifest cases must be a non-empty list")
    prediction_cases = {} if predictions is None else predictions.get("cases", {})
    if not isinstance(prediction_cases, dict):
        raise ValueError("predictions.cases must be an object keyed by fixture")

    ingredient_rows: list[dict[str, Any]] = []
    for case in cases:
        fixture = case["fixture"]
        expected = _normalise_labels(case["visible_ingredient_labels"])
        prediction = prediction_cases.get(fixture)
        if not isinstance(prediction, dict) or "ingredients" not in prediction:
            ingredient_rows.append({"fixture": fixture, "status": "no-prediction"})
            continue
        predicted = _normalise_labels(prediction["ingredients"])
        precision, recall, f1 = _f1(expected, predicted)
        ingredient_rows.append({
            "fixture": fixture,
            "status": "scored",
            "precision": precision,
            "recall": recall,
            "f1": f1,
            "missing": sorted(expected - predicted),
            "unexpected": sorted(predicted - expected),
        })

    scored_ingredients = [row for row in ingredient_rows if row["status"] == "scored"]
    ingredient_policy = manifest["pass_policy"]
    if not scored_ingredients:
        ingredient_result: dict[str, Any] = {
            "status": "not-evaluable",
            "reason": "No ingredient prediction field was supplied; current Food-101 output is one dish label.",
            "ground_truth_case_count": len(cases),
            "scored_case_count": 0,
            "macro_f1": None,
            "pass": False,
            "cases": ingredient_rows,
        }
    else:
        macro_f1 = statistics.fmean(row["f1"] for row in scored_ingredients)
        ingredient_result = {
            "status": "scored",
            "ground_truth_case_count": len(cases),
            "scored_case_count": len(scored_ingredients),
            "macro_f1": macro_f1,
            "pass": (
                len(scored_ingredients) == len(cases)
                and macro_f1 >= ingredient_policy["ingredient_macro_f1_min"]
                and all(row["f1"] >= ingredient_policy["ingredient_case_f1_min"] for row in scored_ingredients)
            ),
            "cases": ingredient_rows,
        }

    ingredient_mass_rows: list[dict[str, Any]] = []
    total_mass_rows: list[dict[str, Any]] = []
    for case in cases:
        fixture = case["fixture"]
        expected_ingredients = case.get("ingredient_masses_g", {})
        prediction = prediction_cases.get(fixture)
        predicted_ingredients = prediction.get("ingredient_masses_g", {}) if isinstance(prediction, dict) else {}
        if not isinstance(expected_ingredients, dict) or not isinstance(predicted_ingredients, dict):
            raise ValueError(f"ingredient_masses_g must be an object for fixture {fixture}")
        for ingredient, expected in expected_ingredients.items():
            predicted = predicted_ingredients.get(ingredient)
            if not isinstance(expected, (int, float)) or not isinstance(predicted, (int, float)):
                ingredient_mass_rows.append({
                    "fixture": fixture,
                    "ingredient": ingredient,
                    "status": "no-calibrated-ground-truth",
                })
                continue
            if not math.isfinite(expected) or not math.isfinite(predicted) or expected <= 0 or predicted < 0:
                raise ValueError(f"Invalid ingredient mass for {fixture}/{ingredient}")
            absolute_error = abs(float(predicted) - float(expected))
            ingredient_mass_rows.append({
                "fixture": fixture,
                "ingredient": ingredient,
                "status": "scored",
                "expected_mass_g": expected,
                "predicted_mass_g": predicted,
                "absolute_error_g": absolute_error,
                "relative_error": absolute_error / float(expected),
            })

        expected_total = case.get("total_mass_g")
        predicted_total = prediction.get("total_mass_g") if isinstance(prediction, dict) else None
        if not isinstance(expected_total, (int, float)) or not isinstance(predicted_total, (int, float)):
            total_mass_rows.append({"fixture": fixture, "status": "no-calibrated-ground-truth"})
        else:
            if not math.isfinite(expected_total) or not math.isfinite(predicted_total) or expected_total <= 0 or predicted_total < 0:
                raise ValueError(f"Invalid total mass for fixture {fixture}")
            absolute_error = abs(float(predicted_total) - float(expected_total))
            total_mass_rows.append({
                "fixture": fixture,
                "status": "scored",
                "expected_mass_g": expected_total,
                "predicted_mass_g": predicted_total,
                "absolute_error_g": absolute_error,
                "relative_error": absolute_error / float(expected_total),
            })

    scored_ingredient_masses = [row for row in ingredient_mass_rows if row["status"] == "scored"]
    scored_total_masses = [row for row in total_mass_rows if row["status"] == "scored"]
    if not scored_ingredient_masses and not scored_total_masses:
        weight_result: dict[str, Any] = {
            "status": "not-evaluable",
            "reason": "Public fixtures have no scale-derived per-ingredient or total mass and the current model emits no mass estimate.",
            "ground_truth_case_count": len(cases),
            "scored_ingredient_count": 0,
            "scored_total_case_count": 0,
            "ingredient_mass_mae_g": None,
            "ingredient_mass_relative_mae": None,
            "ingredient_mass_p90_absolute_error_g": None,
            "total_mass_mae_g": None,
            "total_mass_relative_mae": None,
            "total_mass_p90_absolute_error_g": None,
            "pass": False,
            "ingredient_masses": ingredient_mass_rows,
            "total_masses": total_mass_rows,
        }
    else:
        ingredient_absolute_errors = [row["absolute_error_g"] for row in scored_ingredient_masses]
        ingredient_relative_errors = [row["relative_error"] for row in scored_ingredient_masses]
        total_absolute_errors = [row["absolute_error_g"] for row in scored_total_masses]
        total_relative_errors = [row["relative_error"] for row in scored_total_masses]

        def p90(values: list[float]) -> float | None:
            if not values:
                return None
            index = max(0, min(len(values) - 1, math.ceil(0.9 * len(values)) - 1))
            return sorted(values)[index]

        ingredient_p90 = p90(ingredient_absolute_errors)
        total_p90 = p90(total_absolute_errors)
        weight_result = {
            "status": "scored",
            "ground_truth_case_count": len(cases),
            "scored_ingredient_count": len(scored_ingredient_masses),
            "scored_total_case_count": len(scored_total_masses),
            "ingredient_mass_mae_g": statistics.fmean(ingredient_absolute_errors) if ingredient_absolute_errors else None,
            "ingredient_mass_relative_mae": statistics.fmean(ingredient_relative_errors) if ingredient_relative_errors else None,
            "ingredient_mass_p90_absolute_error_g": ingredient_p90,
            "total_mass_mae_g": statistics.fmean(total_absolute_errors) if total_absolute_errors else None,
            "total_mass_relative_mae": statistics.fmean(total_relative_errors) if total_relative_errors else None,
            "total_mass_p90_absolute_error_g": total_p90,
            "pass": (
                len(scored_ingredient_masses) == sum(len(case.get("ingredient_masses_g", {})) for case in cases)
                and len(scored_total_masses) == len(cases)
                and statistics.fmean(ingredient_relative_errors) <= manifest["pass_policy"]["weight_relative_mae_max"]
                and ingredient_p90 is not None
                and ingredient_p90 <= manifest["pass_policy"]["weight_p90_absolute_error_g_max"]
                and statistics.fmean(total_relative_errors) <= manifest["pass_policy"]["weight_relative_mae_max"]
                and total_p90 is not None
                and total_p90 <= manifest["pass_policy"]["weight_p90_absolute_error_g_max"]
            ),
            "ingredient_masses": ingredient_mass_rows,
            "total_masses": total_mass_rows,
        }

    return {
        "schema": "supernutrition.g3-meal-benchmark-result.v1",
        "ingredient_benchmark": ingredient_result,
        "weight_benchmark": weight_result,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--predictions", type=Path)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = evaluate(_load(args.manifest), _load(args.predictions) if args.predictions else None)
    encoded = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if args.output:
        args.output.write_text(encoded, encoding="utf-8")
    else:
        print(encoded, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
