#!/usr/bin/env python3
"""Tests for the consented weighed-corpus validator."""

from __future__ import annotations

import copy
import importlib.util
import json
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).with_name("validate_weighed_corpus.py")
SPEC = importlib.util.spec_from_file_location("validate_weighed_corpus", MODULE_PATH)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def digest(seed: str) -> str:
    return "sha256:" + (seed * 64)[:64]


def valid_case(partition: str = "held-out", participant_group: str = "participant:p1") -> dict:
    return {
        "caseId": "meal:case-001",
        "consent": {
            "consentRef": "consent:case-001",
            "purpose": "model-training-and-evaluation",
            "retentionPolicyVersion": "retention-1",
            "withdrawalStatus": "active",
            "derivedArtifactsAllowed": True,
        },
        "capture": {
            "views": [
                {
                    "viewId": "view:top",
                    "photoDigest": digest("a"),
                    "deviceRef": "device:phone-1",
                    "angle": "top-down",
                    "lighting": "indoor",
                    "widthPx": 1000,
                    "heightPx": 1000,
                    "depthAvailable": False,
                    "referencePresent": True,
                }
            ]
        },
        "measurements": {
            "scaleRef": "scale:001",
            "scaleResolutionGrams": 1.0,
            "calibrationCheck": "passed",
            "tareMassGrams": 100.0,
            "finalPlateMassGrams": 300.0,
            "componentReadings": [
                {"componentId": "component:beef", "grams": 120.0, "readBeforePlating": True},
                {"componentId": "component:beans", "grams": 80.0, "readBeforePlating": True},
            ],
            "reconciliationToleranceGrams": 5.0,
            "reconciliationDeltaGrams": 0.0,
            "unit": "g",
        },
        "components": [
            {
                "componentId": "component:beef",
                "state": "visible-separable",
                "label": "beef_steak",
                "unknownReason": None,
                "box": {"left": 0.1, "top": 0.1, "right": 0.6, "bottom": 0.8},
                "mask": {
                    "digest": digest("b"),
                    "format": "coco-rle",
                    "widthPx": 1000,
                    "heightPx": 1000,
                },
                "groundTruthMass": {"status": "measured", "grams": 120.0, "reason": None},
            },
            {
                "componentId": "component:beans",
                "state": "visible-separable",
                "label": "green_bean",
                "unknownReason": None,
                "box": {"left": 0.6, "top": 0.2, "right": 0.9, "bottom": 0.7},
                "mask": {
                    "digest": digest("c"),
                    "format": "coco-rle",
                    "widthPx": 1000,
                    "heightPx": 1000,
                },
                "groundTruthMass": {"status": "measured", "grams": 80.0, "reason": None},
            },
        ],
        "quality": {
            "status": "passed",
            "privacyReview": "passed",
            "duplicateReview": "passed",
            "primaryAnnotatorRef": "annotator:aa",
            "secondaryAnnotatorRef": "annotator:bb",
            "adjudicated": True,
            "maskAgreementIoU": 0.9,
            "identityAgreement": True,
        },
        "split": {
            "partition": partition,
            "participantGroup": participant_group,
            "mealGroup": "meal:m1",
            "recipeGroup": "recipe:r1",
            "deviceGroup": "device:d1",
            "sessionGroup": "session:s1",
            "venueGroup": "venue:v1",
        },
    }


def valid_manifest() -> dict:
    return {
        "schema": "supernutrition.consented-weighed-component-corpus.v1",
        "corpusVersion": "v1",
        "taxonomyVersion": "taxonomy-1",
        "annotationProtocolVersion": "annotations-1",
        "retentionPolicyVersion": "retention-1",
        "externalSources": [],
        "cases": [valid_case()],
    }


class ValidateWeighedCorpusTest(unittest.TestCase):
    def test_valid_manifest_reports_measured_component_summary(self) -> None:
        result = MODULE.validate_manifest(valid_manifest())
        self.assertEqual(result["caseCount"], 1)
        self.assertEqual(result["measuredComponentCount"], 2)
        self.assertEqual(result["notEvaluableComponentCount"], 0)

    def test_g3_gate_rejects_small_structural_fixture(self) -> None:
        with self.assertRaisesRegex(MODULE.ValidationError, "at least 300"):
            MODULE.validate_manifest(valid_manifest(), require_g3=True)

    def test_g3_gate_accepts_complete_partition_shape(self) -> None:
        partitions = ("train", "calibration", "held-out")
        cases = []
        for index in range(300):
            case = valid_case(partition=partitions[index % len(partitions)])
            case["caseId"] = f"meal:case-{index:03d}"
            for group_name in (
                "participantGroup",
                "mealGroup",
                "recipeGroup",
                "deviceGroup",
                "sessionGroup",
                "venueGroup",
            ):
                case["split"][group_name] = f"{group_name.lower()}:{index:03d}"
            cases.append(case)
        manifest = valid_manifest()
        manifest["cases"] = cases

        result = MODULE.validate_manifest(manifest, require_g3=True)

        self.assertEqual(result["caseCount"], 300)

    def test_rejects_raw_photo_path_and_unknown_field(self) -> None:
        manifest = valid_manifest()
        manifest["cases"][0]["capture"]["rawPhotoPath"] = "/private/photo.jpg"
        with self.assertRaises(MODULE.ValidationError):
            MODULE.validate_manifest(manifest)

    def test_rejects_missing_component_scale_reading(self) -> None:
        manifest = valid_manifest()
        manifest["cases"][0]["measurements"]["componentReadings"].pop()
        with self.assertRaisesRegex(MODULE.ValidationError, "independent scale reading"):
            MODULE.validate_manifest(manifest)

    def test_rejects_split_leakage_across_partitions(self) -> None:
        manifest = valid_manifest()
        second = copy.deepcopy(manifest["cases"][0])
        second["caseId"] = "meal:case-002"
        second["split"]["partition"] = "train"
        manifest["cases"].append(second)
        with self.assertRaisesRegex(MODULE.ValidationError, "participantGroup"):
            MODULE.validate_manifest(manifest)

    def test_rejects_visible_component_without_mask(self) -> None:
        manifest = valid_manifest()
        manifest["cases"][0]["components"][0]["mask"] = None
        with self.assertRaisesRegex(MODULE.ValidationError, "mask"):
            MODULE.validate_manifest(manifest)

    def test_accepts_explicit_not_evaluable_component_without_mask(self) -> None:
        manifest = valid_manifest()
        case = manifest["cases"][0]
        component = case["components"][1]
        component["label"] = None
        component["state"] = "inseparably-mixed"
        component["unknownReason"] = "mixed-preparation"
        component["box"] = None
        component["mask"] = None
        component["groundTruthMass"] = {
            "status": "not_evaluable",
            "grams": None,
            "reason": "mixed-preparation",
        }
        case["measurements"]["componentReadings"].pop()
        case["measurements"]["finalPlateMassGrams"] = 220.0
        case["measurements"]["reconciliationDeltaGrams"] = 0.0
        result = MODULE.validate_manifest(manifest)
        self.assertEqual(result["notEvaluableComponentCount"], 1)

    def test_rejects_failed_scale_calibration(self) -> None:
        manifest = valid_manifest()
        manifest["cases"][0]["measurements"]["calibrationCheck"] = "failed"
        with self.assertRaisesRegex(MODULE.ValidationError, "calibration"):
            MODULE.validate_manifest(manifest)

    def test_rejects_bundled_external_dataset(self) -> None:
        manifest = valid_manifest()
        manifest["externalSources"] = [
            {
                "name": "Nutrition5k",
                "role": "pretraining",
                "licenseStatus": "reviewed",
                "bundledInApp": True,
                "acceptanceEvidence": False,
            }
        ]
        with self.assertRaisesRegex(MODULE.ValidationError, "bundledInApp"):
            MODULE.validate_manifest(manifest)


if __name__ == "__main__":
    unittest.main()
