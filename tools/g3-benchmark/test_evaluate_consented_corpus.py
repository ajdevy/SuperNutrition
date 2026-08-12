#!/usr/bin/env python3
"""Tests for the fail-closed consented-corpus evaluator."""

from __future__ import annotations

import copy
import importlib.util
import unittest
from pathlib import Path


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


ROOT = Path(__file__).parent
VALIDATOR = load_module("validate_weighed_corpus", ROOT / "validate_weighed_corpus.py")
EVALUATOR = load_module("evaluate_consented_corpus", ROOT / "evaluate_consented_corpus.py")


def predictions_for(manifest: dict, *, include_total: bool = True, mass_delta: float = 0.0) -> dict:
    cases = {}
    for case in manifest["cases"]:
        components = []
        for index, component in enumerate(case["components"]):
            if component["state"] != "visible-separable":
                continue
            components.append({
                "predictionId": f"prediction:{case['caseId'].split(':', 1)[1]}-{index}",
                "label": component["label"],
                "massGrams": component["groundTruthMass"]["grams"] + mass_delta,
                "confidence": 0.99,
                "region": copy.deepcopy(component["box"]),
                "status": "estimated",
            })
        value = {
            "components": components,
            "totalMassGrams": None,
            "runtime": {
                "deviceRef": "device:test-phone",
                "latencyMs": 42.0,
                "peakPssKb": 123456.0,
                "thermalState": "nominal",
            },
        }
        if include_total:
            value["totalMassGrams"] = case["measurements"]["finalPlateMassGrams"] - case["measurements"]["tareMassGrams"] + mass_delta
        cases[case["caseId"]] = value
    return {
        "schema": "supernutrition.g3-corpus-predictions.v1",
        "modelRef": {
            "modelId": "model:test-model",
            "modelDigest": "sha256:" + "1" * 64,
            "calibrationRef": "calibration:test-1",
        },
        "cases": cases,
    }


class EvaluateConsentedCorpusTest(unittest.TestCase):
    def setUp(self) -> None:
        self.manifest = VALIDATOR_TEST_MANIFEST()

    def test_perfect_predictions_pass_and_include_slices(self) -> None:
        result = EVALUATOR.evaluate(self.manifest, predictions_for(self.manifest))

        self.assertEqual(result["status"], "pass")
        self.assertTrue(result["pass"])
        self.assertEqual(result["ingredientBenchmark"]["macroF1"], 1.0)
        self.assertEqual(result["massBenchmark"]["components"]["coverage"], 1.0)
        self.assertIn("held-out", result["slices"]["partition"])
        self.assertIn("indoor", result["slices"]["lighting"])
        self.assertEqual(result["cases"][0]["runtime"]["latencyMs"], 42.0)

    def test_iou_and_label_matching_is_one_to_one(self) -> None:
        predictions = predictions_for(self.manifest)
        case = predictions["cases"]["meal:case-001"]
        case["components"][1]["region"] = {"left": 0.0, "top": 0.0, "right": 0.1, "bottom": 0.1}
        case["components"].append(copy.deepcopy(case["components"][0]))
        case["components"][-1]["predictionId"] = "prediction:duplicate"

        result = EVALUATOR.evaluate(self.manifest, predictions)

        self.assertEqual(result["status"], "fail")
        self.assertEqual(result["ingredientBenchmark"]["truePositiveCount"], 1)
        self.assertEqual(result["ingredientBenchmark"]["predictedComponentCount"], 3)
        self.assertEqual(result["ingredientBenchmark"]["caseF1Min"], 0.4)

    def test_mass_abstention_is_explicit_and_not_evaluable_for_mass(self) -> None:
        predictions = predictions_for(self.manifest, include_total=False)
        predictions["cases"]["meal:case-001"]["components"][0]["massGrams"] = None

        result = EVALUATOR.evaluate(self.manifest, predictions)

        self.assertEqual(result["status"], "fail")
        self.assertEqual(result["massBenchmark"]["components"]["abstentionCount"], 1)
        self.assertEqual(result["massBenchmark"]["components"]["coverage"], 0.5)
        self.assertEqual(result["massBenchmark"]["total"]["status"], "not-evaluable")

    def test_missing_case_cannot_pass(self) -> None:
        second = copy.deepcopy(self.manifest["cases"][0])
        second["caseId"] = "meal:case-002"
        for field in (
            "participantGroup",
            "mealGroup",
            "recipeGroup",
            "deviceGroup",
            "sessionGroup",
            "venueGroup",
        ):
            second["split"][field] = f"{field.lower()}:case-002"
        self.manifest["cases"].append(second)
        predictions = predictions_for(self.manifest)
        predictions["cases"].pop("meal:case-001")

        result = EVALUATOR.evaluate(self.manifest, predictions)

        self.assertEqual(result["status"], "fail")
        self.assertEqual(result["corpus"]["missingCaseCount"], 1)

    def test_empty_predictions_are_not_evaluable(self) -> None:
        predictions = predictions_for(self.manifest)
        predictions["cases"] = {}

        result = EVALUATOR.evaluate(self.manifest, predictions)

        self.assertEqual(result["status"], "not-evaluable")
        self.assertFalse(result["pass"])

    def test_require_g3_delegates_to_corpus_gate(self) -> None:
        with self.assertRaisesRegex(EVALUATOR.EvaluationError, "at least 300"):
            EVALUATOR.evaluate(self.manifest, predictions_for(self.manifest), require_g3=True)

    def test_unknown_prediction_field_is_rejected(self) -> None:
        predictions = predictions_for(self.manifest)
        predictions["cases"]["meal:case-001"]["rawPhotoPath"] = "/private/photo.jpg"

        with self.assertRaises(EVALUATOR.EvaluationError):
            EVALUATOR.evaluate(self.manifest, predictions)


def VALIDATOR_TEST_MANIFEST() -> dict:
    """Keep evaluator tests coupled to the validator's known-good fixture."""
    test_module = load_module("test_validate_weighed_corpus", ROOT / "test_validate_weighed_corpus.py")
    return test_module.valid_manifest()


if __name__ == "__main__":
    unittest.main()
