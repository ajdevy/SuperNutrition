#!/usr/bin/env python3
"""Validate the privacy-safe manifest for the consented weighed corpus.

This validator intentionally accepts metadata and digests only.  It does not
open photos, masks, datasets, or scale exports and it never prints manifest
values in validation errors.
"""

from __future__ import annotations

import argparse
import json
import math
import re
import sys
from pathlib import Path
from typing import Any


SCHEMA = "supernutrition.consented-weighed-component-corpus.v1"
G3_MIN_CASES = 300
OPAQUE_REF = re.compile(r"^[a-z][a-z0-9-]*:[a-z0-9][a-z0-9._-]{1,127}$")
SHA256 = re.compile(r"^sha256:[0-9a-f]{64}$")
LABEL = re.compile(r"^[a-z0-9]+(?:[_-][a-z0-9]+)*$")

TOP_LEVEL_KEYS = {
    "schema",
    "corpusVersion",
    "taxonomyVersion",
    "annotationProtocolVersion",
    "retentionPolicyVersion",
    "externalSources",
    "cases",
}
CASE_KEYS = {"caseId", "consent", "capture", "measurements", "components", "quality", "split"}
CONSENT_KEYS = {
    "consentRef",
    "purpose",
    "retentionPolicyVersion",
    "withdrawalStatus",
    "derivedArtifactsAllowed",
}
VIEW_KEYS = {
    "viewId",
    "photoDigest",
    "deviceRef",
    "angle",
    "lighting",
    "widthPx",
    "heightPx",
    "depthAvailable",
    "referencePresent",
}
MEASUREMENTS_KEYS = {
    "scaleRef",
    "scaleResolutionGrams",
    "calibrationCheck",
    "tareMassGrams",
    "finalPlateMassGrams",
    "componentReadings",
    "reconciliationToleranceGrams",
    "reconciliationDeltaGrams",
    "unit",
}
READING_KEYS = {"componentId", "grams", "readBeforePlating"}
COMPONENT_KEYS = {"componentId", "state", "label", "unknownReason", "box", "mask", "groundTruthMass"}
BOX_KEYS = {"left", "top", "right", "bottom"}
MASK_KEYS = {"digest", "format", "widthPx", "heightPx"}
MASS_KEYS = {"status", "grams", "reason"}
QUALITY_KEYS = {
    "status",
    "privacyReview",
    "duplicateReview",
    "primaryAnnotatorRef",
    "secondaryAnnotatorRef",
    "adjudicated",
    "maskAgreementIoU",
    "identityAgreement",
}
SPLIT_KEYS = {
    "partition",
    "participantGroup",
    "mealGroup",
    "recipeGroup",
    "deviceGroup",
    "sessionGroup",
    "venueGroup",
}
GROUP_FIELDS = (
    "participantGroup",
    "mealGroup",
    "recipeGroup",
    "deviceGroup",
    "sessionGroup",
    "venueGroup",
)
EXTERNAL_SOURCE_KEYS = {"name", "role", "licenseStatus", "bundledInApp", "acceptanceEvidence"}


class ValidationError(ValueError):
    """A redacted manifest validation failure."""


def _fail(path: str, reason: str) -> None:
    raise ValidationError(f"{path}: {reason}")


def _object(value: Any, path: str) -> dict[str, Any]:
    if not isinstance(value, dict):
        _fail(path, "must be an object")
    return value


def _list(value: Any, path: str) -> list[Any]:
    if not isinstance(value, list):
        _fail(path, "must be an array")
    return value


def _keys(value: dict[str, Any], expected: set[str], path: str) -> None:
    missing = expected - value.keys()
    if missing:
        _fail(path, "missing required field")
    if value.keys() - expected:
        _fail(path, "contains an unknown field")


def _string(value: Any, path: str, pattern: re.Pattern[str] | None = None) -> str:
    if not isinstance(value, str) or not value.strip():
        _fail(path, "must be a non-empty string")
    if pattern is not None and not pattern.fullmatch(value):
        _fail(path, "has an invalid format")
    return value


def _bool(value: Any, path: str) -> bool:
    if not isinstance(value, bool):
        _fail(path, "must be boolean")
    return value


def _number(value: Any, path: str, *, minimum: float | None = None, maximum: float | None = None) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(float(value)):
        _fail(path, "must be a finite number")
    number = float(value)
    if minimum is not None and number < minimum:
        _fail(path, "is below the allowed minimum")
    if maximum is not None and number > maximum:
        _fail(path, "is above the allowed maximum")
    return number


def _integer(value: Any, path: str, *, minimum: int = 1) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        _fail(path, "must be a positive integer")
    return value


def _digest(value: Any, path: str) -> str:
    return _string(value, path, SHA256)


def _ref(value: Any, path: str) -> str:
    return _string(value, path, OPAQUE_REF)


def _validate_external_sources(value: Any) -> None:
    sources = _list(value, "externalSources")
    for index, raw_source in enumerate(sources):
        path = f"externalSources[{index}]"
        source = _object(raw_source, path)
        _keys(source, EXTERNAL_SOURCE_KEYS, path)
        _string(source["name"], f"{path}.name")
        _string(source["role"], f"{path}.role")
        if source["role"] not in {"pretraining", "benchmark-design"}:
            _fail(f"{path}.role", "is not an allowed research-only role")
        _string(source["licenseStatus"], f"{path}.licenseStatus")
        if source["licenseStatus"] not in {"reviewed", "not-reviewed"}:
            _fail(f"{path}.licenseStatus", "is not an allowed license state")
        if _bool(source["bundledInApp"], f"{path}.bundledInApp"):
            _fail(f"{path}.bundledInApp", "must be false for external research data")
        if _bool(source["acceptanceEvidence"], f"{path}.acceptanceEvidence"):
            _fail(f"{path}.acceptanceEvidence", "must be false for external research data")


def _validate_consent(value: Any, path: str, retention_version: str) -> None:
    consent = _object(value, path)
    _keys(consent, CONSENT_KEYS, path)
    _ref(consent["consentRef"], f"{path}.consentRef")
    if _string(consent["purpose"], f"{path}.purpose") != "model-training-and-evaluation":
        _fail(f"{path}.purpose", "must use the corpus purpose")
    if _string(consent["retentionPolicyVersion"], f"{path}.retentionPolicyVersion") != retention_version:
        _fail(f"{path}.retentionPolicyVersion", "does not match the corpus policy")
    if _string(consent["withdrawalStatus"], f"{path}.withdrawalStatus") != "active":
        _fail(f"{path}.withdrawalStatus", "withdrawn cases cannot enter a scored corpus")
    if not _bool(consent["derivedArtifactsAllowed"], f"{path}.derivedArtifactsAllowed"):
        _fail(f"{path}.derivedArtifactsAllowed", "must allow the declared evaluation artifacts")


def _validate_capture(value: Any, path: str) -> list[tuple[int, int]]:
    capture = _object(value, path)
    if set(capture) != {"views"}:
        _fail(path, "must contain exactly the views field")
    views = _list(capture["views"], f"{path}.views")
    if not views:
        _fail(f"{path}.views", "must not be empty")
    dimensions: list[tuple[int, int]] = []
    view_ids: set[str] = set()
    for index, raw_view in enumerate(views):
        view_path = f"{path}.views[{index}]"
        view = _object(raw_view, view_path)
        _keys(view, VIEW_KEYS, view_path)
        view_id = _ref(view["viewId"], f"{view_path}.viewId")
        if view_id in view_ids:
            _fail(f"{view_path}.viewId", "is duplicated")
        view_ids.add(view_id)
        _digest(view["photoDigest"], f"{view_path}.photoDigest")
        _ref(view["deviceRef"], f"{view_path}.deviceRef")
        if _string(view["angle"], f"{view_path}.angle") not in {"top-down", "oblique", "multi-view"}:
            _fail(f"{view_path}.angle", "is not an allowed capture angle")
        if _string(view["lighting"], f"{view_path}.lighting") not in {"daylight", "indoor", "mixed", "low-light"}:
            _fail(f"{view_path}.lighting", "is not an allowed lighting state")
        width = _integer(view["widthPx"], f"{view_path}.widthPx")
        height = _integer(view["heightPx"], f"{view_path}.heightPx")
        _bool(view["depthAvailable"], f"{view_path}.depthAvailable")
        _bool(view["referencePresent"], f"{view_path}.referencePresent")
        dimensions.append((width, height))
    return dimensions


def _validate_measurements(value: Any, path: str) -> tuple[dict[str, float], float, float]:
    measurements = _object(value, path)
    _keys(measurements, MEASUREMENTS_KEYS, path)
    _ref(measurements["scaleRef"], f"{path}.scaleRef")
    _number(measurements["scaleResolutionGrams"], f"{path}.scaleResolutionGrams", minimum=0.000001)
    if _string(measurements["calibrationCheck"], f"{path}.calibrationCheck") != "passed":
        _fail(f"{path}.calibrationCheck", "must be passed for scored ground truth")
    tare = _number(measurements["tareMassGrams"], f"{path}.tareMassGrams", minimum=0.0)
    final_plate = _number(measurements["finalPlateMassGrams"], f"{path}.finalPlateMassGrams", minimum=0.0)
    if _string(measurements["unit"], f"{path}.unit") != "g":
        _fail(f"{path}.unit", "must be grams")
    tolerance = _number(measurements["reconciliationToleranceGrams"], f"{path}.reconciliationToleranceGrams", minimum=0.0)
    delta = _number(measurements["reconciliationDeltaGrams"], f"{path}.reconciliationDeltaGrams")
    if abs(delta) > tolerance:
        _fail(f"{path}.reconciliationDeltaGrams", "exceeds the declared reconciliation tolerance")
    readings: dict[str, float] = {}
    for index, raw_reading in enumerate(_list(measurements["componentReadings"], f"{path}.componentReadings")):
        reading_path = f"{path}.componentReadings[{index}]"
        reading = _object(raw_reading, reading_path)
        _keys(reading, READING_KEYS, reading_path)
        component_id = _ref(reading["componentId"], f"{reading_path}.componentId")
        if component_id in readings:
            _fail(f"{reading_path}.componentId", "is duplicated")
        if not _bool(reading["readBeforePlating"], f"{reading_path}.readBeforePlating"):
            _fail(f"{reading_path}.readBeforePlating", "must be true")
        readings[component_id] = _number(reading["grams"], f"{reading_path}.grams", minimum=0.000001)
    if not readings:
        _fail(f"{path}.componentReadings", "must not be empty")
    return readings, tare, final_plate


def _validate_box(value: Any, path: str) -> None:
    box = _object(value, path)
    _keys(box, BOX_KEYS, path)
    left = _number(box["left"], f"{path}.left", minimum=0.0, maximum=1.0)
    top = _number(box["top"], f"{path}.top", minimum=0.0, maximum=1.0)
    right = _number(box["right"], f"{path}.right", minimum=0.0, maximum=1.0)
    bottom = _number(box["bottom"], f"{path}.bottom", minimum=0.0, maximum=1.0)
    if left >= right or top >= bottom:
        _fail(path, "must have positive normalized area")


def _validate_mask(value: Any, path: str, dimensions: list[tuple[int, int]]) -> None:
    mask = _object(value, path)
    _keys(mask, MASK_KEYS, path)
    _digest(mask["digest"], f"{path}.digest")
    if _string(mask["format"], f"{path}.format") not in {"coco-rle", "coco-polygon"}:
        _fail(f"{path}.format", "is not an allowed mask format")
    width = _integer(mask["widthPx"], f"{path}.widthPx")
    height = _integer(mask["heightPx"], f"{path}.heightPx")
    if (width, height) not in dimensions:
        _fail(path, "dimensions do not match a captured view")


def _validate_mass(value: Any, path: str, *, measured: bool) -> str:
    mass = _object(value, path)
    _keys(mass, MASS_KEYS, path)
    status = _string(mass["status"], f"{path}.status")
    if measured:
        if status != "measured":
            _fail(f"{path}.status", "visible separable components must have measured ground truth")
        _number(mass["grams"], f"{path}.grams", minimum=0.000001)
        if mass["reason"] is not None:
            _fail(f"{path}.reason", "must be null for measured ground truth")
    else:
        if status != "not_evaluable":
            _fail(f"{path}.status", "non-visible components must be explicitly not_evaluable")
        _string(mass["reason"], f"{path}.reason")
        if mass["grams"] is not None:
            _fail(f"{path}.grams", "must be null for not_evaluable ground truth")
    return status


def _validate_components(value: Any, path: str, dimensions: list[tuple[int, int]], readings: dict[str, float]) -> tuple[int, int]:
    components = _list(value, path)
    if not components:
        _fail(path, "must not be empty")
    component_ids: set[str] = set()
    measured_count = 0
    not_evaluable_count = 0
    measured_values: dict[str, float] = {}
    for index, raw_component in enumerate(components):
        component_path = f"{path}[{index}]"
        component = _object(raw_component, component_path)
        _keys(component, COMPONENT_KEYS, component_path)
        component_id = _ref(component["componentId"], f"{component_path}.componentId")
        if component_id in component_ids:
            _fail(f"{component_path}.componentId", "is duplicated")
        component_ids.add(component_id)
        state = _string(component["state"], f"{component_path}.state")
        visible = state == "visible-separable"
        if visible:
            label = _string(component["label"], f"{component_path}.label", LABEL)
            if component["unknownReason"] is not None or not label:
                _fail(component_path, "visible component identity is invalid")
            _validate_box(component["box"], f"{component_path}.box")
            _validate_mask(component["mask"], f"{component_path}.mask", dimensions)
            _validate_mass(component["groundTruthMass"], f"{component_path}.groundTruthMass", measured=True)
            measured_count += 1
            measured_values[component_id] = _number(
                component["groundTruthMass"]["grams"], f"{component_path}.groundTruthMass.grams", minimum=0.000001
            )
        else:
            if state not in {"hidden", "occluded", "unknown", "inseparably-mixed"}:
                _fail(f"{component_path}.state", "is not an allowed non-visible state")
            if component["label"] is not None or component["box"] is not None or component["mask"] is not None:
                _fail(component_path, "non-visible component cannot carry visible-region evidence")
            if _string(component["unknownReason"], f"{component_path}.unknownReason") not in {
                "hidden",
                "occluded",
                "unknown-food",
                "mixed-preparation",
                "not-visibly-separable",
            }:
                _fail(f"{component_path}.unknownReason", "is not an allowed non-evaluable reason")
            _validate_mass(component["groundTruthMass"], f"{component_path}.groundTruthMass", measured=False)
            not_evaluable_count += 1
        if component_id not in readings and visible:
            _fail(f"{component_path}.componentId", "has no independent scale reading")
        if component_id in readings and not visible:
            _fail(f"{component_path}.componentId", "non-evaluable component cannot have a scale reading")
    if set(readings) != set(measured_values):
        _fail(path, "scale readings and measured visible components do not match one-to-one")
    for component_id, grams in measured_values.items():
        if abs(grams - readings[component_id]) > 0.000001:
            _fail(path, "component ground truth does not match its scale reading")
    return measured_count, not_evaluable_count


def _validate_quality(value: Any, path: str) -> None:
    quality = _object(value, path)
    _keys(quality, QUALITY_KEYS, path)
    for field in ("status", "privacyReview", "duplicateReview"):
        if _string(quality[field], f"{path}.{field}") != "passed":
            _fail(f"{path}.{field}", "must be passed for a scored case")
    _ref(quality["primaryAnnotatorRef"], f"{path}.primaryAnnotatorRef")
    _ref(quality["secondaryAnnotatorRef"], f"{path}.secondaryAnnotatorRef")
    if not _bool(quality["adjudicated"], f"{path}.adjudicated"):
        _fail(f"{path}.adjudicated", "must be true")
    _number(quality["maskAgreementIoU"], f"{path}.maskAgreementIoU", minimum=0.0, maximum=1.0)
    _bool(quality["identityAgreement"], f"{path}.identityAgreement")


def _validate_split(value: Any, path: str) -> dict[str, str]:
    split = _object(value, path)
    _keys(split, SPLIT_KEYS, path)
    if _string(split["partition"], f"{path}.partition") not in {"train", "calibration", "held-out"}:
        _fail(f"{path}.partition", "is not an allowed partition")
    return {field: _ref(split[field], f"{path}.{field}") for field in GROUP_FIELDS}


def validate_manifest(manifest: Any, *, require_g3: bool = False) -> dict[str, int]:
    document = _object(manifest, "manifest")
    _keys(document, TOP_LEVEL_KEYS, "manifest")
    if _string(document["schema"], "manifest.schema") != SCHEMA:
        _fail("manifest.schema", "does not identify the supported corpus schema")
    if _string(document["corpusVersion"], "manifest.corpusVersion") != "v1":
        _fail("manifest.corpusVersion", "does not identify the supported corpus version")
    taxonomy_version = _string(document["taxonomyVersion"], "manifest.taxonomyVersion")
    annotation_version = _string(document["annotationProtocolVersion"], "manifest.annotationProtocolVersion")
    retention_version = _string(document["retentionPolicyVersion"], "manifest.retentionPolicyVersion")
    _validate_external_sources(document["externalSources"])
    cases = _list(document["cases"], "manifest.cases")
    if not cases:
        _fail("manifest.cases", "must not be empty")
    if require_g3 and len(cases) < G3_MIN_CASES:
        _fail("manifest.cases", f"G-3 corpus requires at least {G3_MIN_CASES} consented cases")
    group_partitions: dict[str, dict[str, str]] = {}
    partitions: set[str] = set()
    measured_count = 0
    not_evaluable_count = 0
    case_ids: set[str] = set()
    for index, raw_case in enumerate(cases):
        case_path = f"manifest.cases[{index}]"
        case = _object(raw_case, case_path)
        _keys(case, CASE_KEYS, case_path)
        case_id = _ref(case["caseId"], f"{case_path}.caseId")
        if case_id in case_ids:
            _fail(f"{case_path}.caseId", "is duplicated")
        case_ids.add(case_id)
        _validate_consent(case["consent"], f"{case_path}.consent", retention_version)
        dimensions = _validate_capture(case["capture"], f"{case_path}.capture")
        readings, tare, final_plate = _validate_measurements(case["measurements"], f"{case_path}.measurements")
        measured, not_evaluable = _validate_components(case["components"], f"{case_path}.components", dimensions, readings)
        _validate_quality(case["quality"], f"{case_path}.quality")
        split = _validate_split(case["split"], f"{case_path}.split")
        partition = case["split"]["partition"]
        partitions.add(partition)
        for group_name, group_id in split.items():
            previous_partition = group_partitions.setdefault(group_id, {}).get(group_name)
            if previous_partition is not None and previous_partition != partition:
                _fail(f"{case_path}.split.{group_name}", "leaks a group across partitions")
            group_partitions[group_id][group_name] = partition
        expected_final = tare + sum(readings.values())
        if abs(final_plate - expected_final) > 0.000001 + float(case["measurements"]["reconciliationToleranceGrams"]):
            _fail(f"{case_path}.measurements", "scale readings do not reconcile with final plate mass")
        measured_count += measured
        not_evaluable_count += not_evaluable
    if require_g3 and partitions != {"train", "calibration", "held-out"}:
        _fail("manifest.cases", "G-3 corpus must contain train, calibration, and held-out partitions")
    return {
        "caseCount": len(cases),
        "measuredComponentCount": measured_count,
        "notEvaluableComponentCount": not_evaluable_count,
        "taxonomyVersionLength": len(taxonomy_version),
        "annotationProtocolVersionLength": len(annotation_version),
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path, help="path to a corpus manifest JSON file")
    parser.add_argument(
        "--require-g3",
        action="store_true",
        help="enforce the minimum pilot size and all required G-3 partitions",
    )
    args = parser.parse_args(argv)
    try:
        with args.manifest.open(encoding="utf-8") as handle:
            manifest = json.load(handle)
        summary = validate_manifest(manifest, require_g3=args.require_g3)
    except (OSError, json.JSONDecodeError, ValidationError) as error:
        print(f"corpus validation failed: {error}", file=sys.stderr)
        return 1
    print(
        "validated weighed corpus: "
        f"{summary['caseCount']} cases, "
        f"{summary['measuredComponentCount']} measured components, "
        f"{summary['notEvaluableComponentCount']} not-evaluable components"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
