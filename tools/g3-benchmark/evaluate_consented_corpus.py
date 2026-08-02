#!/usr/bin/env python3
"""Score a redacted consented corpus against component/mass predictions.

This is the fail-closed evaluator for the post-release G-3 evidence path. It
does not open images, masks, scale exports, or model files.  Both inputs are
strict JSON contracts containing opaque references and digests only.
"""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import math
import statistics
import sys
from pathlib import Path
from typing import Any, Iterable


PREDICTION_SCHEMA = "supernutrition.g3-corpus-predictions.v1"
RESULT_SCHEMA = "supernutrition.g3-consented-corpus-result.v1"
DEFAULT_POLICY = {
    "ingredient_macro_f1_min": 0.85,
    "ingredient_case_f1_min": 0.80,
    "weight_relative_mae_max": 0.15,
    "weight_p90_absolute_error_g_max": 50.0,
}
DEFAULT_IOU_THRESHOLD = 0.5


class EvaluationError(ValueError):
    """A redacted corpus/prediction contract or evaluation failure."""


def _load(path: Path) -> dict[str, Any]:
    try:
        with path.open(encoding="utf-8") as handle:
            value = json.load(handle)
    except (OSError, json.JSONDecodeError) as error:
        raise EvaluationError(f"could not read JSON input: {path.name}") from error
    if not isinstance(value, dict):
        raise EvaluationError("JSON input must be an object")
    return value


def _file_digest(path: Path) -> str:
    try:
        return "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()
    except OSError as error:
        raise EvaluationError(f"could not digest JSON input: {path.name}") from error


def _load_validator() -> Any:
    path = Path(__file__).with_name("validate_weighed_corpus.py")
    spec = importlib.util.spec_from_file_location("validate_weighed_corpus", path)
    if spec is None or spec.loader is None:
        raise EvaluationError("corpus validator is unavailable")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _keys(value: dict[str, Any], expected: set[str], path: str) -> None:
    if expected - value.keys():
        raise EvaluationError(f"{path}: missing required field")
    if value.keys() - expected:
        raise EvaluationError(f"{path}: contains an unknown field")


def _finite_number(value: Any, path: str, *, minimum: float | None = None, maximum: float | None = None) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(float(value)):
        raise EvaluationError(f"{path}: must be a finite number")
    number = float(value)
    if minimum is not None and number < minimum:
        raise EvaluationError(f"{path}: is below the allowed minimum")
    if maximum is not None and number > maximum:
        raise EvaluationError(f"{path}: is above the allowed maximum")
    return number


def _ref(value: Any, path: str, validator: Any) -> str:
    if not isinstance(value, str) or not validator.OPAQUE_REF.fullmatch(value):
        raise EvaluationError(f"{path}: must be an opaque reference")
    return value


def _digest(value: Any, path: str, validator: Any) -> str:
    if not isinstance(value, str) or not validator.SHA256.fullmatch(value):
        raise EvaluationError(f"{path}: must be a sha256 digest")
    return value


def _label(value: Any, path: str, validator: Any) -> str:
    if not isinstance(value, str) or not validator.LABEL.fullmatch(value):
        raise EvaluationError(f"{path}: must be a canonical food label")
    return value


def _normalise_label(value: str) -> str:
    return value.strip().lower()


def _validate_region(value: Any, path: str) -> dict[str, float]:
    if not isinstance(value, dict):
        raise EvaluationError(f"{path}: must be an object")
    _keys(value, {"left", "top", "right", "bottom"}, path)
    left = _finite_number(value["left"], f"{path}.left", minimum=0.0, maximum=1.0)
    top = _finite_number(value["top"], f"{path}.top", minimum=0.0, maximum=1.0)
    right = _finite_number(value["right"], f"{path}.right", minimum=0.0, maximum=1.0)
    bottom = _finite_number(value["bottom"], f"{path}.bottom", minimum=0.0, maximum=1.0)
    if left >= right or top >= bottom:
        raise EvaluationError(f"{path}: must have positive normalized area")
    return {"left": left, "top": top, "right": right, "bottom": bottom}


def _iou(first: dict[str, float], second: dict[str, Any]) -> float:
    left = max(first["left"], float(second["left"]))
    top = max(first["top"], float(second["top"]))
    right = min(first["right"], float(second["right"]))
    bottom = min(first["bottom"], float(second["bottom"]))
    intersection = max(0.0, right - left) * max(0.0, bottom - top)
    first_area = (first["right"] - first["left"]) * (first["bottom"] - first["top"])
    second_area = (float(second["right"]) - float(second["left"])) * (
        float(second["bottom"]) - float(second["top"])
    )
    union = first_area + second_area - intersection
    return intersection / union if union > 0.0 else 0.0


def _validate_predictions(predictions: dict[str, Any], manifest_case_ids: set[str], validator: Any) -> dict[str, Any]:
    _keys(predictions, {"schema", "modelRef", "cases"}, "predictions")
    if predictions["schema"] != PREDICTION_SCHEMA:
        raise EvaluationError("predictions.schema: unsupported prediction schema")
    model_ref = predictions["modelRef"]
    if not isinstance(model_ref, dict):
        raise EvaluationError("predictions.modelRef: must be an object")
    _keys(model_ref, {"modelId", "modelDigest", "calibrationRef"}, "predictions.modelRef")
    _ref(model_ref["modelId"], "predictions.modelRef.modelId", validator)
    _digest(model_ref["modelDigest"], "predictions.modelRef.modelDigest", validator)
    _ref(model_ref["calibrationRef"], "predictions.modelRef.calibrationRef", validator)

    cases = predictions["cases"]
    if not isinstance(cases, dict):
        raise EvaluationError("predictions.cases: must be an object keyed by caseId")
    unknown_cases = set(cases) - manifest_case_ids
    if unknown_cases:
        raise EvaluationError("predictions.cases: contains a case absent from the corpus")
    validated_cases: dict[str, Any] = {}
    for index, (case_id, raw_case) in enumerate(cases.items()):
        case_path = f"predictions.cases[{index}]"
        if not isinstance(case_id, str) or not validator.OPAQUE_REF.fullmatch(case_id):
            raise EvaluationError(f"{case_path}: caseId must be an opaque reference")
        if not isinstance(raw_case, dict):
            raise EvaluationError(f"{case_path}: must be an object")
        allowed_case_keys = {"components", "totalMassGrams"}
        if "runtime" in raw_case:
            allowed_case_keys.add("runtime")
        _keys(raw_case, allowed_case_keys, case_path)
        components = raw_case["components"]
        if not isinstance(components, list):
            raise EvaluationError(f"{case_path}.components: must be an array")
        validated_components: list[dict[str, Any]] = []
        prediction_ids: set[str] = set()
        for index, raw_component in enumerate(components):
            component_path = f"{case_path}.components[{index}]"
            if not isinstance(raw_component, dict):
                raise EvaluationError(f"{component_path}: must be an object")
            _keys(raw_component, {"predictionId", "label", "massGrams", "confidence", "region", "status"}, component_path)
            prediction_id = _ref(raw_component["predictionId"], f"{component_path}.predictionId", validator)
            if prediction_id in prediction_ids:
                raise EvaluationError(f"{component_path}.predictionId: is duplicated")
            prediction_ids.add(prediction_id)
            status = raw_component["status"]
            if status not in {"estimated", "abstained"}:
                raise EvaluationError(f"{component_path}.status: unsupported status")
            label = raw_component["label"]
            region = raw_component["region"]
            mass = raw_component["massGrams"]
            if status == "estimated":
                label = _normalise_label(_label(label, f"{component_path}.label", validator))
                region = _validate_region(region, f"{component_path}.region")
            elif label is not None or region is not None or mass is not None:
                raise EvaluationError(f"{component_path}: abstained component must not carry an estimate")
            if mass is not None:
                mass = _finite_number(mass, f"{component_path}.massGrams", minimum=0.0)
            confidence = _finite_number(raw_component["confidence"], f"{component_path}.confidence", minimum=0.0, maximum=1.0)
            validated_components.append({
                "predictionId": prediction_id,
                "label": label,
                "massGrams": mass,
                "confidence": confidence,
                "region": region,
                "status": status,
            })
        total_mass = raw_case["totalMassGrams"]
        if total_mass is not None:
            total_mass = _finite_number(total_mass, f"{case_path}.totalMassGrams", minimum=0.0)
        runtime = raw_case.get("runtime")
        if runtime is not None:
            if not isinstance(runtime, dict):
                raise EvaluationError(f"{case_path}.runtime: must be an object")
            _keys(runtime, {"deviceRef", "latencyMs", "peakPssKb", "thermalState"}, f"{case_path}.runtime")
            _ref(runtime["deviceRef"], f"{case_path}.runtime.deviceRef", validator)
            _finite_number(runtime["latencyMs"], f"{case_path}.runtime.latencyMs", minimum=0.0)
            _finite_number(runtime["peakPssKb"], f"{case_path}.runtime.peakPssKb", minimum=0.0)
            if not isinstance(runtime["thermalState"], str) or not runtime["thermalState"].strip():
                raise EvaluationError(f"{case_path}.runtime.thermalState: must be a non-empty string")
        validated_cases[case_id] = {
            "components": validated_components,
            "totalMassGrams": total_mass,
            "runtime": runtime,
        }
    return {"modelRef": model_ref, "cases": validated_cases}


def _policy(value: dict[str, Any] | None) -> dict[str, float]:
    if value is None:
        return dict(DEFAULT_POLICY)
    candidate = value.get("pass_policy", value)
    if not isinstance(candidate, dict):
        raise EvaluationError("policy must be an object or contain pass_policy")
    expected = set(DEFAULT_POLICY)
    if set(candidate) != expected:
        raise EvaluationError("policy contains an unsupported or missing threshold")
    return {
        key: _finite_number(candidate[key], f"policy.{key}", minimum=0.0)
        for key in expected
    }


def _p90(values: list[float]) -> float | None:
    if not values:
        return None
    return sorted(values)[max(0, min(len(values) - 1, math.ceil(0.9 * len(values)) - 1))]


def _f1(true_positive: int, predicted: int, expected: int) -> tuple[float, float, float]:
    precision = true_positive / predicted if predicted else 0.0
    recall = true_positive / expected if expected else 1.0
    f1 = 2.0 * precision * recall / (precision + recall) if precision + recall else 0.0
    return precision, recall, f1


def _summarise(case_rows: list[dict[str, Any]], mass_rows: list[dict[str, Any]], total_rows: list[dict[str, Any]]) -> dict[str, Any]:
    expected_components = sum(row["expectedCount"] for row in case_rows)
    predicted_components = sum(row["predictedCount"] for row in case_rows)
    true_positive = sum(row["truePositive"] for row in case_rows)
    precisions = [row["precision"] for row in case_rows]
    recalls = [row["recall"] for row in case_rows]
    f1s = [row["f1"] for row in case_rows]
    component_precision, component_recall, component_f1 = _f1(true_positive, predicted_components, expected_components)
    scored_masses = [row for row in mass_rows if row["status"] == "scored"]
    mass_abs = [row["absoluteErrorGrams"] for row in scored_masses]
    mass_rel = [row["relativeError"] for row in scored_masses]
    total_scored = [row for row in total_rows if row["status"] == "scored"]
    total_abs = [row["absoluteErrorGrams"] for row in total_scored]
    total_rel = [row["relativeError"] for row in total_scored]

    def mass_summary(errors: list[float], relatives: list[float], denominator: int, abstentions: int) -> dict[str, Any]:
        return {
            "status": "scored" if errors else "not-evaluable",
            "groundTruthCount": denominator,
            "scoredCount": len(errors),
            "coverage": len(errors) / denominator if denominator else 0.0,
            "abstentionCount": abstentions,
            "maeGrams": statistics.fmean(errors) if errors else None,
            "relativeMae": statistics.fmean(relatives) if relatives else None,
            "p90AbsoluteErrorGrams": _p90(errors),
            "biasGrams": statistics.fmean(
                row["signedErrorGrams"] for row in mass_rows if row["status"] == "scored"
            ) if errors else None,
        }

    total_bias = statistics.fmean(row["signedErrorGrams"] for row in total_scored) if total_scored else None
    return {
        "caseCount": len(case_rows),
        "ingredient": {
            "status": "scored" if case_rows else "not-evaluable",
            "caseCount": len(case_rows),
            "macroF1": statistics.fmean(f1s) if f1s else None,
            "macroPrecision": statistics.fmean(precisions) if precisions else None,
            "macroRecall": statistics.fmean(recalls) if recalls else None,
            "componentPrecision": component_precision if case_rows else None,
            "componentRecall": component_recall if case_rows else None,
            "componentF1": component_f1 if case_rows else None,
            "expectedComponentCount": expected_components,
            "predictedComponentCount": predicted_components,
            "truePositiveCount": true_positive,
            "caseF1Min": min(f1s) if f1s else None,
        },
        "mass": {
            "components": mass_summary(
                mass_abs,
                mass_rel,
                expected_components,
                sum(1 for row in mass_rows if row["status"] == "abstained"),
            ),
            "total": {
                "status": "scored" if total_abs else "not-evaluable",
                "groundTruthCount": len(total_rows),
                "scoredCount": len(total_scored),
                "coverage": len(total_scored) / len(total_rows) if total_rows else 0.0,
                "abstentionCount": sum(1 for row in total_rows if row["status"] == "abstained"),
                "maeGrams": statistics.fmean(total_abs) if total_abs else None,
                "relativeMae": statistics.fmean(total_rel) if total_rel else None,
                "p90AbsoluteErrorGrams": _p90(total_abs),
                "biasGrams": total_bias,
            },
        },
    }


def _evaluate_case(case: dict[str, Any], prediction: dict[str, Any] | None, iou_threshold: float) -> tuple[dict[str, Any], list[dict[str, Any]], dict[str, Any]]:
    expected = [
        component
        for component in case["components"]
        if component["state"] == "visible-separable" and component["groundTruthMass"]["status"] == "measured"
    ]
    prediction_components = [] if prediction is None else [
        component for component in prediction["components"] if component["status"] == "estimated"
    ]
    candidates: list[tuple[float, int, int]] = []
    for prediction_index, predicted in enumerate(prediction_components):
        for expected_index, actual in enumerate(expected):
            if predicted["label"] == actual["label"]:
                candidates.append((
                    _iou(predicted["region"], actual["box"]),
                    prediction_index,
                    expected_index,
                ))
    matched: dict[int, tuple[int, float]] = {}
    matched_expected: set[int] = set()
    for overlap, prediction_index, expected_index in sorted(candidates, key=lambda row: (-row[0], row[1], row[2])):
        if overlap < iou_threshold or prediction_index in matched or expected_index in matched_expected:
            continue
        matched[prediction_index] = (expected_index, overlap)
        matched_expected.add(expected_index)
    precision, recall, f1 = _f1(len(matched), len(prediction_components), len(expected))
    missing_labels = sorted(expected[index]["label"] for index in range(len(expected)) if index not in matched_expected)
    unexpected_labels = sorted(
        prediction_components[index]["label"] for index in range(len(prediction_components)) if index not in matched
    )
    case_result = {
        "caseId": case["caseId"],
        "partition": case["split"]["partition"],
        "deviceGroup": case["split"]["deviceGroup"],
        "lighting": sorted({view["lighting"] for view in case["capture"]["views"]}),
        "angles": sorted({view["angle"] for view in case["capture"]["views"]}),
        "predictionStatus": "present" if prediction is not None else "missing",
        "expectedCount": len(expected),
        "predictedCount": len(prediction_components),
        "truePositive": len(matched),
        "precision": precision,
        "recall": recall,
        "f1": f1,
        "missingLabels": missing_labels,
        "unexpectedLabels": unexpected_labels,
    }
    if prediction is not None and prediction["runtime"] is not None:
        case_result["runtime"] = prediction["runtime"]
    mass_rows: list[dict[str, Any]] = []
    for prediction_index, (expected_index, overlap) in matched.items():
        actual_mass = float(expected[expected_index]["groundTruthMass"]["grams"])
        predicted_mass = prediction_components[prediction_index]["massGrams"]
        if predicted_mass is None:
            mass_rows.append({"caseId": case["caseId"], "status": "abstained"})
            continue
        signed = float(predicted_mass) - actual_mass
        mass_rows.append({
            "caseId": case["caseId"],
            "status": "scored",
            "overlap": overlap,
            "expectedGrams": actual_mass,
            "predictedGrams": float(predicted_mass),
            "signedErrorGrams": signed,
            "absoluteErrorGrams": abs(signed),
            "relativeError": abs(signed) / actual_mass,
        })
    mass_rows.extend(
        {"caseId": case["caseId"], "status": "abstained"}
        for _ in range(len(expected) - len(matched))
    )
    expected_total = float(case["measurements"]["finalPlateMassGrams"]) - float(case["measurements"]["tareMassGrams"])
    predicted_total = None if prediction is None else prediction["totalMassGrams"]
    if predicted_total is None:
        total_row = {"caseId": case["caseId"], "status": "abstained"}
    else:
        signed = float(predicted_total) - expected_total
        total_row = {
            "caseId": case["caseId"],
            "status": "scored",
            "expectedGrams": expected_total,
            "predictedGrams": float(predicted_total),
            "signedErrorGrams": signed,
            "absoluteErrorGrams": abs(signed),
            "relativeError": abs(signed) / expected_total if expected_total else 0.0,
        }
    return case_result, mass_rows, total_row


def _slice_rows(case_rows: list[dict[str, Any]], kind: str) -> dict[str, list[dict[str, Any]]]:
    result: dict[str, list[dict[str, Any]]] = {}
    for row in case_rows:
        if kind == "partition":
            values: Iterable[str] = (row["partition"],)
        elif kind == "device":
            values = (row["deviceGroup"],)
        elif kind == "lighting":
            values = row["lighting"]
        else:
            values = row["angles"]
        for value in values:
            result.setdefault(value, []).append(row)
    return result


def evaluate(
    manifest: dict[str, Any],
    predictions: dict[str, Any],
    *,
    require_g3: bool = False,
    iou_threshold: float = DEFAULT_IOU_THRESHOLD,
    policy: dict[str, Any] | None = None,
) -> dict[str, Any]:
    validator = _load_validator()
    try:
        validator.validate_manifest(manifest, require_g3=require_g3)
    except validator.ValidationError as error:
        raise EvaluationError(str(error)) from error
    threshold = _finite_number(iou_threshold, "iou_threshold", minimum=0.0, maximum=1.0)
    resolved_policy = _policy(policy)
    case_list = manifest["cases"]
    case_ids = {case["caseId"] for case in case_list}
    validated_predictions = _validate_predictions(predictions, case_ids, validator)
    prediction_cases = validated_predictions["cases"]
    case_rows: list[dict[str, Any]] = []
    mass_rows: list[dict[str, Any]] = []
    total_rows: list[dict[str, Any]] = []
    for case in case_list:
        row, component_masses, total_mass = _evaluate_case(case, prediction_cases.get(case["caseId"]), threshold)
        case_rows.append(row)
        mass_rows.extend(component_masses)
        total_rows.append(total_mass)
    summary = _summarise(case_rows, mass_rows, total_rows)
    ingredient = summary["ingredient"]
    component_mass = summary["mass"]["components"]
    total_mass = summary["mass"]["total"]
    missing_cases = sorted(case_ids - prediction_cases.keys())
    ingredient_pass = (
        len(missing_cases) == 0
        and ingredient["macroF1"] is not None
        and ingredient["macroF1"] >= resolved_policy["ingredient_macro_f1_min"]
        and ingredient["caseF1Min"] is not None
        and ingredient["caseF1Min"] >= resolved_policy["ingredient_case_f1_min"]
    )
    mass_pass = (
        component_mass["coverage"] == 1.0
        and total_mass["coverage"] == 1.0
        and component_mass["relativeMae"] is not None
        and component_mass["relativeMae"] <= resolved_policy["weight_relative_mae_max"]
        and component_mass["p90AbsoluteErrorGrams"] is not None
        and component_mass["p90AbsoluteErrorGrams"] <= resolved_policy["weight_p90_absolute_error_g_max"]
        and total_mass["relativeMae"] is not None
        and total_mass["relativeMae"] <= resolved_policy["weight_relative_mae_max"]
        and total_mass["p90AbsoluteErrorGrams"] is not None
        and total_mass["p90AbsoluteErrorGrams"] <= resolved_policy["weight_p90_absolute_error_g_max"]
    )
    evaluated = bool(prediction_cases) and ingredient["status"] == "scored"
    status = "pass" if evaluated and ingredient_pass and mass_pass else ("fail" if evaluated else "not-evaluable")
    slices: dict[str, Any] = {}
    for kind in ("partition", "device", "lighting", "angle"):
        values = _slice_rows(case_rows, kind)
        slices[kind] = {
            value: _summarise(
                rows,
                [row for row in mass_rows if row["caseId"] in {item["caseId"] for item in rows}],
                [row for row in total_rows if row["caseId"] in {item["caseId"] for item in rows}],
            )
            for value, rows in sorted(values.items())
        }
    return {
        "schema": RESULT_SCHEMA,
        "status": status,
        "pass": status == "pass",
        "requireG3": require_g3,
        "iouThreshold": threshold,
        "policy": resolved_policy,
        "modelRef": validated_predictions["modelRef"],
        "corpus": {
            "caseCount": len(case_list),
            "predictionCaseCount": len(prediction_cases),
            "missingCaseCount": len(missing_cases),
            "missingCases": missing_cases,
        },
        "ingredientBenchmark": {
            **ingredient,
            "pass": ingredient_pass,
        },
        "massBenchmark": {
            "components": {**component_mass, "pass": mass_pass},
            "total": {**total_mass, "pass": mass_pass},
            "pass": mass_pass,
        },
        "slices": slices,
        "cases": case_rows,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--policy", type=Path, help="JSON containing pass_policy, defaulting to the G-3 policy")
    parser.add_argument("--iou-threshold", type=float, default=DEFAULT_IOU_THRESHOLD)
    parser.add_argument("--require-g3", action="store_true", help="require the 300-case corpus and all partitions")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    try:
        manifest = _load(args.manifest)
        predictions = _load(args.predictions)
        policy = _load(args.policy) if args.policy else None
        result = evaluate(
            manifest,
            predictions,
            require_g3=args.require_g3,
            iou_threshold=args.iou_threshold,
            policy=policy,
        )
        result["inputDigests"] = {
            "corpusManifest": _file_digest(args.manifest),
            "predictions": _file_digest(args.predictions),
            **({"policy": _file_digest(args.policy)} if args.policy else {}),
        }
    except (EvaluationError, ValueError, OSError) as error:
        print(f"G-3 corpus evaluation failed: {error}", file=sys.stderr)
        return 1
    encoded = json.dumps(result, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    try:
        if args.output:
            args.output.write_text(encoded, encoding="utf-8")
        else:
            print(encoded, end="")
    except OSError as error:
        print(f"G-3 corpus evaluation failed: could not write output: {error}", file=sys.stderr)
        return 1
    return 0 if result["pass"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
