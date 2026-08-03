#!/usr/bin/env python3
"""Focused tests for the E0 suggestion-policy replay."""

import json
import sys
import unittest
from pathlib import Path


TOOLS_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOLS_DIR))

import replay_policy  # noqa: E402


class ReplayPolicyTest(unittest.TestCase):
    def test_top_k_support_is_weighted_and_normalized(self) -> None:
        support = replay_policy.prior_support(
            [
                {"label": "pork_chop", "confidence": 0.8},
                {"label": "garden_salad", "confidence": 0.2},
            ],
            2,
        )
        self.assertAlmostEqual(support["tomato"], 0.2)
        self.assertAlmostEqual(max(replay_policy.normalize_support(support).values()), 1.0)

    def test_soft_policies_keep_raw_visual_regions(self) -> None:
        raw = [{"label": "broccoli", "confidence": 0.7, "box": [0.1, 0.1, 0.3, 0.3]}]
        dishes = [{"label": "pork_chop", "confidence": 0.9}]
        for name in ("no_fusion", "top1_soft_support", "topk_union", "bounded_late_fusion"):
            output = replay_policy.build_strategy(name, raw, dishes)
            self.assertEqual(output["overlay_region_count"], 1, name)
            self.assertTrue(output["decision_provenance"]["raw_visual_proposals_retained"], name)
            self.assertNotIn("broccoli", output["composition_labels"], name)

    def test_hard_policy_is_the_only_policy_that_can_remove_a_region(self) -> None:
        raw = [{"label": "broccoli", "confidence": 0.7, "box": [0.1, 0.1, 0.3, 0.3]}]
        dishes = [{"label": "pork_chop", "confidence": 0.9}]
        output = replay_policy.build_strategy("hard_top1_dish_prior", raw, dishes)
        self.assertEqual(output["overlay_region_count"], 0)
        self.assertFalse(output["decision_provenance"]["raw_visual_proposals_retained"])

    def test_hard_replay_matches_existing_weak_proxy_baseline(self) -> None:
        comparison = json.loads(
            (TOOLS_DIR / "data/nutrition5k-100/comparison.json").read_text(encoding="utf-8")
        )
        output_cases = []
        for case in comparison["cases"]:
            strategy = replay_policy.build_strategy("hard_top1_dish_prior", case["raw_regions"], case["dish_top3"])
            output_cases.append(
                {
                    "weak_detector_targets": case["weak_detector_targets"],
                    "visual_labels": strategy["visual_labels"],
                }
            )
        metrics = replay_policy.aggregate(output_cases, "visual_labels")
        baseline = comparison["strategies"]["hard_top1_dish_prior"]["metrics"]
        self.assertEqual(metrics["micro_true_positive"], baseline["micro_true_positive"])
        self.assertEqual(metrics["micro_false_positive"], baseline["micro_false_positive"])
        self.assertEqual(metrics["micro_false_negative"], baseline["micro_false_negative"])
        self.assertEqual(metrics["nonempty_weak_target_cases"], baseline["nonempty_weak_target_cases"])


if __name__ == "__main__":
    unittest.main()
