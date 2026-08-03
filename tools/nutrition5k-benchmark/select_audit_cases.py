#!/usr/bin/env python3
"""Select the deterministic E1 conflict/control audit slice."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


def select_cases(comparison: dict[str, Any], replay: dict[str, Any]) -> list[dict[str, Any]]:
    replay_by_id = {case["case_id"]: case for case in replay["cases"]}
    cases = []
    for case in comparison["cases"]:
        replay_case = replay_by_id[case["case_id"]]
        raw_labels = set(replay_case["strategies"]["no_fusion"]["visual_labels"])
        hard_labels = set(replay_case["strategies"]["hard_top1_dish_prior"]["visual_labels"])
        top1_prior = bool(case["dish_prior"])
        if raw_labels != hard_labels:
            reason = "hard_gate_conflict"
            priority = 0
        elif not raw_labels:
            reason = "empty_raw_visual_output"
            priority = 1
        elif len(raw_labels) > 1:
            reason = "multi_component_control"
            priority = 3
        elif not top1_prior:
            reason = "empty_top1_prior_control"
            priority = 2
        else:
            reason = "unchanged_single_component_control"
            priority = 4
        cases.append(
            {
                "case_id": case["case_id"],
                "dish_id": case["dish_id"],
                "crop_file": case["crop_file"],
                "selection_reason": reason,
                "selection_priority": priority,
                "dish_top3": case["dish_top3"],
                "dish_prior": case["dish_prior"],
                "raw_visual_labels": sorted(raw_labels),
                "hard_visual_labels": sorted(hard_labels),
                "weak_detector_targets": case["weak_detector_targets"],
                "review": {
                    "visible_components": [],
                    "visible_regions": [],
                    "ambiguous_components": [],
                    "composition_only_hypotheses": [],
                    "useful_suggestions": [],
                    "misleading_suggestions": [],
                    "abstention_needed": None,
                    "reviewer": None,
                    "review_status": "pending",
                },
            }
        )

    selected: list[dict[str, Any]] = []
    selected_ids: set[str] = set()

    def pick(predicate: Any, reason: str, limit: int) -> None:
        matching = [case for case in cases if predicate(case) and case["case_id"] not in selected_ids]
        for case in matching[:limit]:
            chosen = dict(case)
            chosen["selection_reason"] = reason
            selected.append(chosen)
            selected_ids.add(case["case_id"])

    pick(lambda case: case["selection_reason"] == "hard_gate_conflict", "hard_gate_conflict", 16)
    pick(lambda case: not case["raw_visual_labels"], "empty_raw_visual_output", 8)
    pick(
        lambda case: not case["dish_prior"] and bool(case["raw_visual_labels"]),
        "empty_top1_prior_control",
        8,
    )
    pick(lambda case: len(case["raw_visual_labels"]) > 1, "multi_component_control", 8)
    pick(lambda case: len(case["raw_visual_labels"]) == 1, "unchanged_single_component_control", 10)
    if len(selected) < 50:
        for case in cases:
            if case["case_id"] not in selected_ids:
                chosen = dict(case)
                chosen["selection_reason"] = "deterministic_control_fill"
                selected.append(chosen)
                selected_ids.add(case["case_id"])
            if len(selected) == 50:
                break
    if len(selected) != 50:
        raise ValueError(f"expected 50 audit cases, selected {len(selected)}")
    if len({case["case_id"] for case in selected}) != 50:
        raise ValueError("audit selection contains duplicate case IDs")
    if sum(case["selection_reason"] == "hard_gate_conflict" for case in selected) != 16:
        raise ValueError("audit selection must contain all 16 hard-gate conflicts")
    return selected


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--comparison", type=Path, required=True)
    parser.add_argument("--replay", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    comparison = json.loads(args.comparison.read_text(encoding="utf-8"))
    replay = json.loads(args.replay.read_text(encoding="utf-8"))
    selected = select_cases(comparison, replay)
    result = {
        "schema": "supernutrition.nutrition5k-visible-audit.v1",
        "status": "pending_annotation",
        "sample_count": len(selected),
        "selection": {
            "all_hard_gate_conflicts": True,
            "control_policy": "deterministic_priority_slice_from_existing_100",
            "ground_truth_boundary": "model-assisted audit is not human ground truth",
        },
        "cases": selected,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"selected {len(selected)} audit cases")
    for reason in sorted({case["selection_reason"] for case in selected}):
        print(reason, sum(case["selection_reason"] == reason for case in selected))
    print(f"wrote {args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
