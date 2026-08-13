#!/usr/bin/env bash
# Read-only validation of the modality-specific inference-evidence/v3 release.
set -euo pipefail

repo_root=$(cd "$(dirname "$BASH_SOURCE")/.." && pwd)
contract_root="$repo_root/contracts/inference/v3"
if printenv SUPERNUTRITION_INFERENCE_V3_CONTRACT >/dev/null; then
    contract_root=$(printenv SUPERNUTRITION_INFERENCE_V3_CONTRACT)
fi

python3 - "$contract_root" <<'PY'
from hashlib import sha256
import json
import math
from pathlib import Path
import re
import sys
import tomllib

root = Path(sys.argv[1]).resolve()
IDENTIFIER = re.compile(r"[A-Za-z0-9._:-]{1,128}")
LOCALE = re.compile(r"[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")
NUTRITION_KEYS = {
    "calories", "calorie", "energy", "energykcal", "macros", "macronutrients",
    "protein", "proteingrams", "carbohydrates", "carbs", "fat", "fatgrams",
    "micronutrients", "nutrition", "nutrients",
}
EVIDENCE_KEYS = {
    "rawphotobytes", "photobytes", "photopath", "photouri", "filepath", "filename",
    "usertext", "inputtext", "transportbody", "requestbody", "responsebody",
    "modelbytes", "modelweights", "mask", "maskref", "catalogbinding", "catalogid",
}
MODALITIES = {"photo", "text"}
ROUTES = {"local", "local-text", "remote"}
CAPABILITY_STATES = {
    "unavailable", "model-required", "downloading", "ready",
    "temporarily-unavailable", "unsupported",
}
CAPABILITY_REASONS = {
    "none", "device-unsupported", "model-missing", "model-downloading",
    "resource-budget", "temporary-unavailable",
}


def fail(message):
    raise SystemExit(f"inference contract rejected: {message}")


def duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def reject_constant(value):
    raise ValueError(f"non-finite JSON number: {value}")


def load_json(path):
    try:
        return json.loads(
            path.read_text(),
            object_pairs_hook=duplicate_keys,
            parse_constant=reject_constant,
        )
    except (OSError, ValueError, json.JSONDecodeError) as error:
        fail(f"{path.name} is not strict JSON: {error}")


def require_exact(value, expected, label):
    if not isinstance(value, dict) or set(value) != set(expected):
        actual = sorted(value) if isinstance(value, dict) else type(value).__name__
        fail(f"{label} fields drifted: expected {sorted(expected)}, got {actual}")


def require_nonblank(value, label):
    if not isinstance(value, str) or not value.strip():
        fail(f"{label} must be a nonblank string")


def require_identifier(value, label):
    if not isinstance(value, str) or not IDENTIFIER.fullmatch(value):
        fail(f"{label} must be a bounded identifier")


def require_locale(value, label):
    if not isinstance(value, str) or not LOCALE.fullmatch(value):
        fail(f"{label} must be a bounded locale")


def require_number(value, label, lower=None, upper=None, strict_lower=False):
    if isinstance(value, bool) or not isinstance(value, (int, float)) or not math.isfinite(float(value)):
        fail(f"{label} must be a finite number")
    if lower is not None and (value <= lower if strict_lower else value < lower):
        fail(f"{label} is below its lower bound")
    if upper is not None and value > upper:
        fail(f"{label} is above its upper bound")


def scan_forbidden(node, origin):
    if isinstance(node, dict):
        for key, value in node.items():
            normalized = key.lower()
            if normalized in NUTRITION_KEYS:
                fail(f"{origin} carries numeric nutrition field '{key}'")
            if normalized in EVIDENCE_KEYS:
                fail(f"{origin} carries forbidden raw evidence field '{key}'")
            scan_forbidden(value, origin)
    elif isinstance(node, list):
        for item in node:
            scan_forbidden(item, origin)


def verify_digest():
    matrix_path = root / "compatibility-matrix.toml"
    actual = tomllib.loads(matrix_path.read_text()).get("digest")
    if not isinstance(actual, str) or not re.fullmatch(r"[a-f0-9]{64}", actual):
        fail("v3 compatibility matrix must carry a lowercase 64-character digest")
    normalize = re.compile(rb'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|pending|<release-digest>)"\s*$')
    digest = sha256()
    files = []
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            fail("v3 release must not contain symlinks")
        if path.is_file():
            content = path.read_bytes()
            if path == matrix_path:
                content = normalize.sub(b'digest = "<release-digest>"', content)
            files.append((str(path.relative_to(root)), content))
    for relative, content in sorted(files):
        digest.update(relative.encode())
        digest.update(b"\0")
        digest.update(content)
    if digest.hexdigest() != actual:
        fail(f"v3 release digest is stale: expected {digest.hexdigest()}, got {actual}")


for required in ("schema.toml", "compatibility-matrix.toml", "RELEASE.md"):
    if not (root / required).is_file():
        fail(f"missing v3 contract file: {root / required}")
fixture_paths = sorted((root / "fixtures").glob("*.json"))
expected_fixture_names = {
    "request-photo.json", "request-text.json",
    "capability-ready-photo.json", "capability-ready-text.json",
    "photo-component-estimated-result.json",
    "photo-component-not-evaluable-result.json",
    "text-candidate-result.json",
    "abstention-result.json", "failure-result.json", "telemetry-event.json",
}
if {path.name for path in fixture_paths} != expected_fixture_names:
    fail("v3 fixture set drifted")

schema = tomllib.loads((root / "schema.toml").read_text())
matrix = tomllib.loads((root / "compatibility-matrix.toml").read_text())
if {
    schema.get("schema"), schema.get("schema_version"), schema.get("contract_id"),
    schema.get("release"), schema.get("previous_release"),
} != {
    "supernutrition.inference-evidence-contract.v3", 1, "inference-evidence",
    "v3", "inference-evidence/v2",
}:
    fail("v3 schema identity or previous release drifted")
if schema.get("language_neutral") is not True or schema.get("executable") is not False:
    fail("v3 must remain language-neutral data only")
if schema.get("canonical_json") is not True or schema.get("numeric_nutrition") is not False:
    fail("v3 canonical JSON or no-nutrition rule drifted")
if schema.get("canonical_fixture_kinds") != ["request", "capability", "result", "telemetry"]:
    fail("v3 fixture kinds drifted")
if schema.get("modality", {}).get("values") != ["photo", "text"]:
    fail("v3 modality vocabulary drifted")
if schema.get("modality", {}).get("cardinality") != "exactly-one":
    fail("v3 must require exactly one modality payload")
compatibility = schema.get("compatibility", {})
if compatibility.get("unknown_fields") != "ignore" or compatibility.get("missing_required_fields") != "reject":
    fail("v3 compatibility rules drifted")
if compatibility.get("coexists_with") != ["photo-inference-engine/v1", "inference-evidence/v2"]:
    fail("v3 coexistence declarations drifted")

if matrix.get("schema") != "supernutrition.inference-evidence-compatibility-matrix.v3":
    fail("v3 compatibility matrix schema drifted")
if matrix.get("contract_id") != "inference-evidence" or matrix.get("release") != "v3":
    fail("v3 compatibility matrix identity drifted")
expected_pairs = {
    ("domain-inference-v3", consumer, "forward", "accepted")
    for consumer in ("local-android-v3", "local-ios-v3", "text-v3", "remote-v3", "evaluation-v3")
}
actual_pairs = {
    (item.get("producer"), item.get("consumer"), item.get("direction"), item.get("expected"))
    for item in matrix.get("supported_pair", [])
}
if actual_pairs != expected_pairs or len(actual_pairs) != len(matrix.get("supported_pair", [])):
    fail("v3 supported compatibility pairs drifted")
expected_coexistence = {
    ("inference-evidence/v2", "inference-evidence/v3"),
    ("inference-evidence/v3", "inference-evidence/v2"),
    ("photo-inference-engine/v1", "inference-evidence/v3"),
    ("inference-evidence/v3", "photo-inference-engine/v1"),
}
actual_coexistence = {
    (item.get("first_release"), item.get("second_release"))
    for item in matrix.get("coexistence_pair", [])
    if item.get("requirement") == "separate-ports-required"
}
if actual_coexistence != expected_coexistence:
    fail("v3 coexistence matrix drifted")

fixtures = {path.name: load_json(path) for path in fixture_paths}
for name, fixture in fixtures.items():
    scan_forbidden(fixture, name)


def verify_provenance(value, label, modality):
    require_exact(value, {
        "route", "engineId", "modelVersion", "taxonomyVersion",
        "calibrationVersion", "contractVersion",
    }, label)
    if value["route"] not in ROUTES:
        fail(f"{label}.route is unknown")
    if modality == "text" and value["route"] == "local":
        fail(f"{label}.route must preserve the local-text distinction")
    if modality == "photo" and value["route"] == "local-text":
        fail(f"{label}.route cannot be local-text for a photo")
    for field in ("engineId", "modelVersion", "taxonomyVersion", "calibrationVersion"):
        require_nonblank(value[field], f"{label}.{field}")
    if value["contractVersion"] != "inference-evidence/v3":
        fail(f"{label}.contractVersion is not v3")


def verify_confidence(value, label):
    require_exact(value, {"score", "band"}, label)
    require_number(value["score"], f"{label}.score", 0, 1)
    expected = "low" if value["score"] < 0.5 else "medium" if value["score"] < 0.8 else "high"
    if value["band"] != expected:
        fail(f"{label}.band does not match its score")


def verify_mass(value, label):
    kind = value.get("massType") if isinstance(value, dict) else None
    if kind == "estimated":
        require_exact(value, {"massType", "range", "calibrationAssumptions"}, label)
        require_exact(value["range"], {"lowerGrams", "upperGrams"}, f"{label}.range")
        require_number(value["range"]["lowerGrams"], f"{label}.range.lowerGrams", 0)
        require_number(value["range"]["upperGrams"], f"{label}.range.upperGrams", 0, strict_lower=True)
        if value["range"]["lowerGrams"] >= value["range"]["upperGrams"]:
            fail(f"{label}.range must be strictly ordered")
        assumptions = value["calibrationAssumptions"]
        if not isinstance(assumptions, list) or not assumptions or len(set(assumptions)) != len(assumptions):
            fail(f"{label}.calibrationAssumptions must be nonempty and unique")
        if not set(assumptions) <= {"single-view-approximation", "reference-geometry-available", "partial-occlusion"}:
            fail(f"{label}.calibrationAssumptions contains an unknown value")
    elif kind == "not-evaluable":
        require_exact(value, {"massType", "reason", "userMessageKey"}, label)
        if value["reason"] not in {
            "ambiguous-portion", "mixed-or-occluded", "unsupported-geometry",
            "missing-calibration", "text-has-no-visible-portion",
        }:
            fail(f"{label}.reason is unknown")
        require_identifier(value["userMessageKey"], f"{label}.userMessageKey")
    elif kind == "abstained":
        require_exact(value, {"massType", "reason", "userMessageKey"}, label)
        if value["reason"] not in {"identity-ambiguous", "image-quality", "unsupported-content"}:
            fail(f"{label}.reason is unknown")
        require_identifier(value["userMessageKey"], f"{label}.userMessageKey")
    else:
        fail(f"{label} must have one known mass state")
    return kind


def verify_component(value, label):
    allowed = {
        "componentId", "candidate", "confidence", "visibility",
        "ambiguity", "region", "mass", "provenance",
    }
    if not isinstance(value, dict) or set(value) not in (allowed, allowed - {"region"}):
        fail(f"{label} fields drifted")
    require_identifier(value["componentId"], f"{label}.componentId")
    require_exact(value["candidate"], {"candidateId", "label", "locale"}, f"{label}.candidate")
    require_identifier(value["candidate"]["candidateId"], f"{label}.candidate.candidateId")
    require_nonblank(value["candidate"]["label"], f"{label}.candidate.label")
    require_locale(value["candidate"]["locale"], f"{label}.candidate.locale")
    verify_confidence(value["confidence"], f"{label}.confidence")
    if value["visibility"] not in {"visible", "partially-occluded"}:
        fail(f"{label}.visibility is unknown")
    if value["ambiguity"] not in {"none", "identity-ambiguous", "mixed-content"}:
        fail(f"{label}.ambiguity is unknown")
    if "region" in value:
        require_exact(value["region"], {"left", "top", "width", "height"}, f"{label}.region")
        for field in ("left", "top", "width", "height"):
            require_number(value["region"][field], f"{label}.region.{field}", 0, 1)
        if value["region"]["width"] == 0 or value["region"]["height"] == 0:
            fail(f"{label}.region dimensions must be positive")
        if value["region"]["left"] + value["region"]["width"] > 1 + 1e-9:
            fail(f"{label}.region exceeds the right image bound")
        if value["region"]["top"] + value["region"]["height"] > 1 + 1e-9:
            fail(f"{label}.region exceeds the bottom image bound")
    mass_kind = verify_mass(value["mass"], f"{label}.mass")
    verify_provenance(value["provenance"], f"{label}.provenance", "photo")
    return mass_kind


def verify_candidate(value, label):
    require_exact(value, {
        "candidateId", "label", "rank", "confidence",
        "unresolvedAssumptions", "provenance",
    }, label)
    require_identifier(value["candidateId"], f"{label}.candidateId")
    require_nonblank(value["label"], f"{label}.label")
    if not isinstance(value["rank"], int) or isinstance(value["rank"], bool) or value["rank"] < 1:
        fail(f"{label}.rank must be positive")
    verify_confidence(value["confidence"], f"{label}.confidence")
    if not isinstance(value["unresolvedAssumptions"], list):
        fail(f"{label}.unresolvedAssumptions must be a list")
    for index, assumption in enumerate(value["unresolvedAssumptions"]):
        require_exact(assumption, {"code", "userMessageKey"}, f"{label}.unresolvedAssumptions[{index}]")
        require_identifier(assumption["code"], f"{label}.unresolvedAssumptions[{index}].code")
        require_identifier(assumption["userMessageKey"], f"{label}.unresolvedAssumptions[{index}].userMessageKey")
    verify_provenance(value["provenance"], f"{label}.provenance", "text")


def verify_result(name, value):
    require_exact(value, {"contractVersion", "modality", "outcome"}, name)
    if value["contractVersion"] != "inference-evidence/v3":
        fail(f"{name} must use v3")
    modality = value["modality"]
    if modality not in MODALITIES:
        fail(f"{name} has an unknown modality")
    outcome = value["outcome"]
    kind = outcome.get("outcomeType") if isinstance(outcome, dict) else None
    if kind == "components":
        if modality != "photo":
            fail(f"{name} component output must be photo modality")
        require_exact(outcome, {"outcomeType", "components"}, f"{name}.outcome")
        components = outcome["components"]
        if not isinstance(components, list) or not components:
            fail(f"{name} component output must not be empty")
        ids = []
        for index, component in enumerate(components):
            ids.append(component.get("componentId") if isinstance(component, dict) else None)
            verify_component(component, f"{name}.components[{index}]")
        if len(ids) != len(set(ids)):
            fail(f"{name} component IDs must be unique")
        return
    if kind == "candidates":
        if modality != "text":
            fail(f"{name} candidate output must be text modality")
        require_exact(outcome, {"outcomeType", "itemGroups"}, f"{name}.outcome")
        groups = outcome["itemGroups"]
        if not isinstance(groups, list) or not groups:
            fail(f"{name} candidate output must not be empty")
        group_ids = []
        description = fixtures["request-text.json"]["text"]["description"]
        for index, group in enumerate(groups):
            require_exact(group, {"groupId", "sourceSpan", "candidates"}, f"{name}.itemGroups[{index}]")
            group_ids.append(group["groupId"])
            require_identifier(group["groupId"], f"{name}.itemGroups[{index}].groupId")
            require_exact(group["sourceSpan"], {"start", "end"}, f"{name}.itemGroups[{index}].sourceSpan")
            start, end = group["sourceSpan"]["start"], group["sourceSpan"]["end"]
            if not isinstance(start, int) or not isinstance(end, int) or not 0 <= start < end <= len(description):
                fail(f"{name}.itemGroups[{index}].sourceSpan does not index the description")
            if description[start:end] != description[start:end].strip():
                fail(f"{name}.itemGroups[{index}].sourceSpan has edge whitespace")
            candidates = group["candidates"]
            if not isinstance(candidates, list) or not candidates:
                fail(f"{name}.itemGroups[{index}] must contain candidates")
            ranks = []
            for candidate_index, candidate in enumerate(candidates):
                ranks.append(candidate.get("rank") if isinstance(candidate, dict) else None)
                verify_candidate(candidate, f"{name}.itemGroups[{index}].candidates[{candidate_index}]")
            if sorted(ranks) != list(range(1, len(ranks) + 1)):
                fail(f"{name}.itemGroups[{index}] ranks must be contiguous from one")
        if len(group_ids) != len(set(group_ids)):
            fail(f"{name} item group IDs must be unique")
        return
    if kind == "abstention":
        require_exact(outcome, {"outcomeType", "reason", "userMessageKey", "provenance"}, f"{name}.outcome")
        if outcome["reason"] not in {
            "identity-ambiguous", "no-food-detected", "image-quality",
            "portion-ambiguous", "unsupported-content", "invalid-input",
            "description-unsegmentable",
        }:
            fail(f"{name} has an unknown abstention reason")
        require_identifier(outcome["userMessageKey"], f"{name}.outcome.userMessageKey")
        verify_provenance(outcome["provenance"], f"{name}.outcome.provenance", modality)
        return
    if kind == "failure":
        require_exact(outcome, {"outcomeType", "code", "retryable", "userMessageKey", "provenance"}, f"{name}.outcome")
        if outcome["code"] not in {
            "invalid-input", "model-unavailable", "resource-limit", "temporary-unavailable",
        } or not isinstance(outcome["retryable"], bool):
            fail(f"{name} has an invalid failure code or retryability")
        require_identifier(outcome["userMessageKey"], f"{name}.outcome.userMessageKey")
        verify_provenance(outcome["provenance"], f"{name}.outcome.provenance", modality)
        return
    fail(f"{name} has an unknown outcome type")


def verify_request(name, value, modality, payload_key):
    require_exact(value, {
        "contractVersion", "modality", "attemptId", "countryProfileId",
        "permittedRoute", payload_key,
    }, name)
    if value["contractVersion"] != "inference-evidence/v3" or value["modality"] != modality:
        fail(f"{name} contract version or modality discriminator drifted")
    require_identifier(value["attemptId"], f"{name}.attemptId")
    require_identifier(value["countryProfileId"], f"{name}.countryProfileId")
    if value["permittedRoute"] not in ROUTES:
        fail(f"{name}.permittedRoute is unknown")
    if modality == "photo":
        if value["permittedRoute"] == "local-text":
            fail(f"{name} photo request cannot permit local-text")
        require_exact(value[payload_key], {"photoRefId"}, f"{name}.photo")
        require_identifier(value[payload_key]["photoRefId"], f"{name}.photo.photoRefId")
    else:
        if value["permittedRoute"] == "local":
            fail(f"{name} text request cannot flatten to local")
        require_exact(value[payload_key], {"description", "inputLocale"}, f"{name}.text")
        require_nonblank(value[payload_key]["description"], f"{name}.text.description")
        require_locale(value[payload_key]["inputLocale"], f"{name}.text.inputLocale")


expected_capability_fields = [
    "contractVersion", "modality", "state", "reason", "userMessageKey",
    "engineId", "modelVersion", "taxonomyVersion", "calibrationVersion",
]
capability_schema = schema.get("capability", {})
if capability_schema.get("emitted_fields") != expected_capability_fields or capability_schema.get("optional_fields") != []:
    fail("v3 capability field allowlist drifted")
for name in ("capability-ready-photo.json", "capability-ready-text.json"):
    capability = fixtures[name]
    require_exact(capability, expected_capability_fields, name)
    if capability["contractVersion"] != "inference-evidence/v3" or capability["modality"] not in MODALITIES:
        fail(f"{name} has an invalid contract version or modality")
    if capability["state"] not in CAPABILITY_STATES or capability["reason"] not in CAPABILITY_REASONS:
        fail(f"{name} has an invalid capability state or reason")
    if (capability["state"] == "ready") != (capability["reason"] == "none"):
        fail(f"{name} capability state and reason disagree")
    for field in ("userMessageKey", "engineId"):
        require_identifier(capability[field], f"{name}.{field}")
    for field in ("modelVersion", "taxonomyVersion", "calibrationVersion"):
        require_nonblank(capability[field], f"{name}.{field}")

verify_request("request-photo.json", fixtures["request-photo.json"], "photo", "photo")
verify_request("request-text.json", fixtures["request-text.json"], "text", "text")
verify_result("photo-component-estimated-result.json", fixtures["photo-component-estimated-result.json"])
verify_result("photo-component-not-evaluable-result.json", fixtures["photo-component-not-evaluable-result.json"])
verify_result("text-candidate-result.json", fixtures["text-candidate-result.json"])
verify_result("abstention-result.json", fixtures["abstention-result.json"])
verify_result("failure-result.json", fixtures["failure-result.json"])

telemetry = fixtures["telemetry-event.json"]
telemetry_schema = schema.get("telemetry", {})
expected_telemetry_fields = [
    "contractVersion", "modality", "eventName", "route", "capabilityTier",
    "durationBucket", "resourceBucket", "terminalOutcome",
    "itemGroupCountBucket", "componentCountBucket", "engineId", "modelVersion",
    "taxonomyVersion", "calibrationVersion",
]
if telemetry_schema.get("emitted_fields") != expected_telemetry_fields:
    fail("v3 telemetry field allowlist drifted")
require_exact(telemetry, expected_telemetry_fields, "telemetry-event.json")
if telemetry["contractVersion"] != "inference-evidence/v3":
    fail("telemetry must use v3")
if telemetry["modality"] not in MODALITIES or telemetry["route"] not in ROUTES:
    fail("telemetry modality or route is unknown")
if telemetry["modality"] == "text" and telemetry["route"] == "local":
    fail("telemetry flattens a text route to local")
if telemetry["modality"] == "photo" and telemetry["route"] == "local-text":
    fail("telemetry assigns a text route to a photo")
schema_vocabularies = {
    "eventName": ("event_name", {"inference.attempt.completed"}),
    "route": ("route", ROUTES),
    "capabilityTier": ("capability_tier", CAPABILITY_STATES),
    "durationBucket": ("duration_bucket", {"under-2s", "2-to-5s", "over-5s"}),
    "resourceBucket": ("resource_bucket", {"low", "moderate", "high"}),
    "terminalOutcome": ("terminal_outcome", {"candidates", "components", "abstention", "failure", "cancelled"}),
    "itemGroupCountBucket": ("item_group_count_bucket", {"none", "1", "2-3", "4-plus"}),
    "componentCountBucket": ("component_count_bucket", {"zero", "one", "two-to-four", "five-or-more"}),
}
for field, (schema_key, values) in schema_vocabularies.items():
    if set(telemetry_schema.get(schema_key, [])) != values:
        fail(f"telemetry vocabulary {field} disagrees with schema")
    if telemetry[field] not in values:
        fail(f"telemetry {field} has an unknown value")
for field in ("engineId", "modelVersion", "taxonomyVersion", "calibrationVersion"):
    require_nonblank(telemetry[field], f"telemetry.{field}")

verify_digest()
print("inference-evidence contract: OK (v3 photo components, text spans, closed telemetry, and coexistence matrix)")
PY
