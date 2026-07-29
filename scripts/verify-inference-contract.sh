#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
contract_root="${SUPERNUTRITION_INFERENCE_CONTRACT:-${repo_root}/contracts/inference/v1}"

python3 - "$contract_root" <<'PY'
from hashlib import sha256
import json
from pathlib import Path
import re
import sys
import tomllib

root = Path(sys.argv[1]).resolve()
schema_path = root / "schema.toml"
matrix_path = root / "compatibility-matrix.toml"
release_path = root / "RELEASE.md"
fixture_paths = sorted((root / "fixtures").glob("*.json"))
for path in (schema_path, matrix_path, release_path, *fixture_paths):
    if not path.is_file():
        raise SystemExit(f"missing inference contract file: {path}")
if {path.name for path in fixture_paths} != {
    "abstention-result.json",
    "capability-ready.json",
    "candidate-result.json",
    "failure-result.json",
    "telemetry-event.json",
}:
    raise SystemExit("inference v1 must publish exactly the three outcome and one telemetry fixture")

schema = tomllib.loads(schema_path.read_text())
matrix = tomllib.loads(matrix_path.read_text())
if schema.get("contract_id") != "photo-inference-engine" or schema.get("release") != "v1":
    raise SystemExit("inference schema must identify the photo-inference-engine v1 release")
if schema.get("language_neutral") is not True or schema.get("executable") is not False:
    raise SystemExit("inference contract must remain language-neutral data only")
if schema.get("numeric_nutrition") is not False:
    raise SystemExit("inference contract must forbid numeric nutrition")
if schema.get("canonical_fixture_kinds") != ["capability", "result", "telemetry"]:
    raise SystemExit("inference contract fixture kinds drifted")
if matrix.get("contract_id") != "photo-inference-engine" or matrix.get("release") != "v1":
    raise SystemExit("inference compatibility matrix must target v1")
compatibility = schema.get("compatibility", {})
if compatibility != {
    "unknown_fields": "ignore",
    "missing_required_fields": "reject",
    "incompatible_change": "new-major-release",
}:
    raise SystemExit("inference compatibility policy drifted")

required_pairs = {
    ("domain-inference-v1", consumer, "forward", "accepted")
    for consumer in ("local-android-v1", "local-ios-v1", "remote-v1", "evaluation-v1")
}
pairs = {
    (
        item.get("producer"),
        item.get("consumer"),
        item.get("direction"),
        item.get("expected"),
    )
    for item in matrix.get("supported_pair", [])
}
if pairs != required_pairs:
    raise SystemExit(f"inference compatibility matrix drifted: {sorted(pairs)}")

forbidden_keys = {
    "calories", "calorie", "energy", "energykcal", "macros", "macronutrients",
    "protein", "proteingrams", "carbohydrates", "carbs", "fat", "fatgrams",
    "micronutrients", "nutrition", "nutrients",
}

def reject_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def reject_nonfinite_numbers(value):
    raise ValueError(f"non-finite JSON number is forbidden: {value}")


def load_fixture(path):
    text = path.read_text()
    try:
        value = json.loads(
            text,
            object_pairs_hook=reject_duplicate_keys,
            parse_constant=reject_nonfinite_numbers,
        )
    except (ValueError, json.JSONDecodeError) as error:
        raise SystemExit(f"{path.name} is not canonical JSON: {error}") from error
    canonical = json.dumps(
        value,
        ensure_ascii=False,
        allow_nan=False,
        separators=(",", ":"),
    ) + "\n"
    if text != canonical:
        raise SystemExit(f"{path.name} is not canonical minified JSON")
    return value

def reject_numeric_nutrition(value):
    if isinstance(value, dict):
        for key, nested in value.items():
            if key.lower() in forbidden_keys:
                raise SystemExit(f"numeric nutrition field is forbidden in inference evidence: {key}")
            reject_numeric_nutrition(nested)
    elif isinstance(value, list):
        for nested in value:
            reject_numeric_nutrition(nested)


fixtures = {path.name: load_fixture(path) for path in fixture_paths}
required_provenance = {
    "route", "engineId", "modelVersion", "calibrationVersion", "contractVersion"
}
required_candidate = {
    "candidateId", "label", "rank", "confidence", "unresolvedAssumptions", "provenance"
}
required_confidence = {"score", "band"}
required_assumption = {"code", "userMessageKey"}
required_telemetry = {
    "contractVersion", "eventType", "route", "capabilityState", "outcome",
    "durationBucket", "resourceBucket",
}
capability_states = {
    "unavailable", "model-required", "downloading", "ready",
    "temporarily-unavailable", "unsupported",
}
capability_reasons = {
    "none", "device-unsupported", "model-missing", "model-downloading",
    "resource-budget", "temporary-unavailable",
}
routes = {"local", "remote"}
outcome_types = {"candidates", "abstention", "failure"}
confidence_bands = {"low", "medium", "high"}
assumption_codes = {
    "portion-unknown", "preparation-unknown", "hidden-ingredients-unknown", "mixed-dish"
}
abstention_reasons = {
    "identity-ambiguous", "no-food-detected", "image-quality", "portion-ambiguous",
    "unsupported-content",
}
failure_codes = {"invalid-input", "model-unavailable", "resource-limit", "temporary-unavailable"}
duration_buckets = {"under-1s", "1-to-5s", "over-5s"}
resource_buckets = {"within-budget", "near-budget", "over-budget"}

def require_keys(value, expected, label):
    if set(value) != expected:
        raise SystemExit(f"{label} fields drifted: expected {sorted(expected)}, got {sorted(value)}")


def require_nonblank(value, label):
    if not isinstance(value, str) or not value.strip():
        raise SystemExit(f"{label} must be a nonblank string")


def validate_provenance(value, label):
    require_keys(value, required_provenance, label)
    if value["route"] not in routes:
        raise SystemExit(f"{label} has an unknown route")
    for field in ("engineId", "modelVersion", "calibrationVersion"):
        require_nonblank(value[field], f"{label}.{field}")
    if value["contractVersion"] != "photo-inference-engine/v1":
        raise SystemExit(f"{label} has an incompatible contract version")


def validate_candidate(value, label):
    require_keys(value, required_candidate, label)
    require_nonblank(value["candidateId"], f"{label}.candidateId")
    require_nonblank(value["label"], f"{label}.label")
    if not isinstance(value["rank"], int) or isinstance(value["rank"], bool) or value["rank"] <= 0:
        raise SystemExit(f"{label}.rank must be a positive integer")
    require_keys(value["confidence"], required_confidence, f"{label}.confidence")
    score = value["confidence"]["score"]
    if not isinstance(score, (int, float)) or isinstance(score, bool) or not 0 <= score <= 1:
        raise SystemExit(f"{label}.confidence.score must be between zero and one")
    if value["confidence"]["band"] not in confidence_bands:
        raise SystemExit(f"{label}.confidence.band is unknown")
    for index, assumption in enumerate(value["unresolvedAssumptions"]):
        require_keys(assumption, required_assumption, f"{label}.unresolvedAssumptions[{index}]")
        if assumption["code"] not in assumption_codes:
            raise SystemExit(f"{label}.unresolvedAssumptions[{index}].code is unknown")
        require_nonblank(assumption["userMessageKey"], f"{label}.unresolvedAssumptions[{index}].userMessageKey")
    validate_provenance(value["provenance"], f"{label}.provenance")


def validate_result_fixture(name, fixture):
    require_keys(fixture, {"contractVersion", "outcome"}, name)
    outcome = fixture["outcome"]
    outcome_type = outcome.get("outcomeType")
    if outcome_type not in outcome_types:
        raise SystemExit(f"{name} has an unknown outcome type")
    if outcome_type == "candidates":
        require_keys(outcome, {"outcomeType", "candidates"}, f"{name}.outcome")
        candidates = outcome["candidates"]
        if not isinstance(candidates, list) or not candidates:
            raise SystemExit(f"{name} must contain at least one candidate")
        for index, candidate in enumerate(candidates):
            validate_candidate(candidate, f"{name}.candidates[{index}]")
        ranks = [candidate["rank"] for candidate in candidates]
        if len(set(ranks)) != len(ranks) or sorted(ranks) != list(range(1, len(ranks) + 1)):
            raise SystemExit(f"{name} candidate ranks must be unique, contiguous, and start at one")
        ids = [candidate["candidateId"] for candidate in candidates]
        if len(set(ids)) != len(ids):
            raise SystemExit(f"{name} candidate IDs must be unique")
    elif outcome_type == "abstention":
        require_keys(outcome, {"outcomeType", "reason", "userMessageKey", "provenance"}, f"{name}.outcome")
        if outcome["reason"] not in abstention_reasons:
            raise SystemExit(f"{name} has an unknown abstention reason")
        require_nonblank(outcome["userMessageKey"], f"{name}.outcome.userMessageKey")
        validate_provenance(outcome["provenance"], f"{name}.outcome.provenance")
    else:
        require_keys(outcome, {"outcomeType", "code", "retryable", "userMessageKey", "provenance"}, f"{name}.outcome")
        if outcome["code"] not in failure_codes:
            raise SystemExit(f"{name} has an unknown failure code")
        if not isinstance(outcome["retryable"], bool):
            raise SystemExit(f"{name}.outcome.retryable must be boolean")
        require_nonblank(outcome["userMessageKey"], f"{name}.outcome.userMessageKey")
        validate_provenance(outcome["provenance"], f"{name}.outcome.provenance")


for name, fixture in fixtures.items():
    if fixture.get("contractVersion") != "photo-inference-engine/v1":
        raise SystemExit(f"{name} must use contract-v1")
    reject_numeric_nutrition(fixture)
    if name == "capability-ready.json":
        require_keys(fixture, {
            "contractVersion", "state", "reason", "userMessageKey", "engineId", "modelVersion"
        }, name)
        if fixture["state"] not in capability_states or fixture["reason"] not in capability_reasons:
            raise SystemExit(f"{name} has an unknown capability state or reason")
        if fixture["state"] == "ready" and fixture["reason"] != "none":
            raise SystemExit(f"{name} ready capability must use reason none")
        for field in ("userMessageKey", "engineId", "modelVersion"):
            require_nonblank(fixture[field], f"{name}.{field}")
        continue
    if name == "telemetry-event.json":
        require_keys(fixture, required_telemetry, name)
        if fixture["eventType"] not in {"attempt", "capability"}:
            raise SystemExit("telemetry fixture has an unknown event type")
        if fixture["outcome"] not in {"candidates", "abstention", "failure", "cancelled"}:
            raise SystemExit("telemetry fixture has an unknown outcome")
        if fixture["route"] not in routes or fixture["capabilityState"] not in capability_states:
            raise SystemExit("telemetry fixture has an unknown route or capability state")
        if fixture["durationBucket"] not in duration_buckets or fixture["resourceBucket"] not in resource_buckets:
            raise SystemExit("telemetry fixture has an unknown timing or resource bucket")
        continue
    validate_result_fixture(name, fixture)

matrix_bytes = re.sub(
    rb'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|pending)"\s*$',
    b'digest = "<release-digest>"',
    matrix_path.read_bytes(),
)
hash_value = sha256()
release_files = [("compatibility-matrix.toml", matrix_bytes), ("schema.toml", schema_path.read_bytes())]
release_files.extend(
    (str(path.relative_to(root)), path.read_bytes()) for path in fixture_paths
)
for relative, content in sorted(release_files):
    hash_value.update(relative.encode("utf-8"))
    hash_value.update(b"\0")
    hash_value.update(content)
if matrix.get("digest") != hash_value.hexdigest():
    raise SystemExit("inference compatibility digest is stale")

allowed = {"RELEASE.md", "schema.toml", "compatibility-matrix.toml"}
if any(path.is_symlink() or (path.name not in allowed and path.suffix != ".json") for path in root.rglob("*") if path.is_file()):
    raise SystemExit("inference contract release contains executable or source material")
print("photo-inference-engine contract: OK (v1 ports, canonical evidence, and compatibility matrix)")
PY
