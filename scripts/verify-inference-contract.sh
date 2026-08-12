#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
if [[ $# -gt 0 ]]; then
    contract_roots=("$@")
elif [[ -n "${SUPERNUTRITION_INFERENCE_CONTRACT:-}" ]]; then
    contract_roots=("${SUPERNUTRITION_INFERENCE_CONTRACT}")
else
    contract_roots=(
        "${repo_root}/contracts/inference/v1"
        # The modality-neutral inference-engine/v3 release has its own validator,
        # scripts/verify-inference-v3-contract.sh. This script validates the
        # inference-evidence release, which lives under its own contract_id.
        "${repo_root}/contracts/inference-evidence/v2"
    )
fi

python3 - "${contract_roots[@]}" <<'PY'
from hashlib import sha256
import json
import math
from pathlib import Path
import re
import sys
import tomllib

V1_VERSION = "photo-inference-engine/v1"
V2_VERSION = "inference-evidence/v2"
IDENTIFIER = re.compile(r"[A-Za-z0-9._:-]{1,128}")
LOCALE = re.compile(r"[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")
FORBIDDEN_NUTRITION_KEYS = {
    "calories", "calorie", "energy", "energykcal", "macros", "macronutrients",
    "protein", "proteingrams", "carbohydrates", "carbs", "fat", "fatgrams",
    "micronutrients", "nutrition", "nutrients",
}
FORBIDDEN_EVIDENCE_KEYS = {
    "rawphotobytes", "photobytes", "photopath", "photouri", "filepath", "filename",
    "usertext", "inputtext", "transportbody", "requestbody", "responsebody",
    "modelbytes", "modelweights", "mask", "maskref", "catalogbinding", "catalogid",
}
CAPABILITY_STATES = {
    "unavailable", "model-required", "downloading", "ready",
    "temporarily-unavailable", "unsupported",
}
CAPABILITY_REASONS = {
    "none", "device-unsupported", "model-missing", "model-downloading",
    "resource-budget", "temporary-unavailable",
}
ROUTES = {"local", "remote"}
DURATION_BUCKETS = {"under-1s", "1-to-5s", "over-5s"}
RESOURCE_BUCKETS = {"within-budget", "near-budget", "over-budget"}


def fail(message):
    raise SystemExit(message)


def reject_duplicate_keys(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def reject_nonfinite_numbers(value):
    raise ValueError(f"non-finite JSON number is forbidden: {value}")


def require_keys(value, expected, label):
    if not isinstance(value, dict) or set(value) != expected:
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
        fail(f"{label} must be a bounded locale tag")


def require_number_between(value, lower, upper, label, lower_inclusive=True):
    if not isinstance(value, (int, float)) or isinstance(value, bool) or not lower <= value <= upper:
        fail(f"{label} must be between {lower} and {upper}")
    try:
        finite_double = float(value)
    except (OverflowError, ValueError):
        fail(f"{label} must be representable as a finite Kotlin Double")
    if not math.isfinite(finite_double):
        fail(f"{label} must be representable as a finite Kotlin Double")
    if not lower_inclusive and value == lower:
        fail(f"{label} must be greater than {lower}")


def reject_forbidden_fields(value):
    if isinstance(value, dict):
        for key, nested in value.items():
            normalized = key.lower()
            if normalized in FORBIDDEN_NUTRITION_KEYS:
                fail(f"numeric nutrition field is forbidden in inference evidence: {key}")
            if normalized in FORBIDDEN_EVIDENCE_KEYS:
                fail(f"raw or transport field is forbidden in inference evidence: {key}")
            reject_forbidden_fields(nested)
    elif isinstance(value, list):
        for nested in value:
            reject_forbidden_fields(nested)


def load_release(root_argument):
    input_root = Path(root_argument)
    if input_root.is_symlink():
        fail(f"inference contract root must not be a symlink: {input_root}")
    root = input_root.resolve()
    schema_path = root / "schema.toml"
    matrix_path = root / "compatibility-matrix.toml"
    release_path = root / "RELEASE.md"
    fixture_paths = sorted((root / "fixtures").glob("*.json"))
    for path in (schema_path, matrix_path, release_path, *fixture_paths):
        if not path.is_file():
            fail(f"missing inference contract file: {path}")
    if any(path.is_symlink() for path in root.rglob("*")):
        fail("inference contract release must not contain symlinks")
    fixtures = {}
    for path in fixture_paths:
        text = path.read_text(encoding="utf-8")
        try:
            value = json.loads(
                text,
                object_pairs_hook=reject_duplicate_keys,
                parse_constant=reject_nonfinite_numbers,
            )
        except (ValueError, json.JSONDecodeError) as error:
            fail(f"{path.name} is not canonical JSON: {error}")
        canonical = json.dumps(value, ensure_ascii=False, allow_nan=False, separators=(",", ":")) + "\n"
        if text != canonical:
            fail(f"{path.name} is not canonical minified JSON")
        fixtures[path.name] = value
    allowed = {
        "RELEASE.md",
        "schema.toml",
        "compatibility-matrix.toml",
        *(str(path.relative_to(root)) for path in fixture_paths),
    }
    if any(
        str(path.relative_to(root)) not in allowed
        for path in root.rglob("*")
        if path.is_file()
    ):
        fail("inference contract release contains executable or source material")
    return {
        "root": root,
        "schema_path": schema_path,
        "matrix_path": matrix_path,
        "schema": tomllib.loads(schema_path.read_text(encoding="utf-8")),
        "matrix": tomllib.loads(matrix_path.read_text(encoding="utf-8")),
        "fixture_paths": fixture_paths,
        "fixtures": fixtures,
    }


def verify_digest(release):
    matrix_bytes = re.sub(
        rb'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|pending)"\s*$',
        b'digest = "<release-digest>"',
        release["matrix_path"].read_bytes(),
    )
    hash_value = sha256()
    root = release["root"]
    release_files = [
        ("RELEASE.md", (root / "RELEASE.md").read_bytes()),
        ("compatibility-matrix.toml", matrix_bytes),
        ("schema.toml", release["schema_path"].read_bytes()),
    ]
    release_files.extend(
        (str(path.relative_to(root)), path.read_bytes())
        for path in release["fixture_paths"]
    )
    for relative, content in sorted(release_files):
        hash_value.update(relative.encode("utf-8"))
        hash_value.update(b"\0")
        hash_value.update(content)
    if release["matrix"].get("digest") != hash_value.hexdigest():
        fail("inference compatibility digest is stale")


def validate_v1(release):
    expected_fixtures = {
        "abstention-result.json",
        "capability-ready.json",
        "candidate-result.json",
        "failure-result.json",
        "telemetry-event.json",
    }
    if set(release["fixtures"]) != expected_fixtures:
        fail("inference v1 must publish exactly three result, one capability, and one telemetry fixture")
    schema = release["schema"]
    matrix = release["matrix"]
    if schema.get("contract_id") != "photo-inference-engine" or schema.get("release") != "v1":
        fail("inference schema must identify the photo-inference-engine v1 release")
    if schema.get("language_neutral") is not True or schema.get("executable") is not False:
        fail("inference contract must remain language-neutral data only")
    if schema.get("numeric_nutrition") is not False:
        fail("inference contract must forbid numeric nutrition")
    if schema.get("canonical_fixture_kinds") != ["capability", "result", "telemetry"]:
        fail("inference contract fixture kinds drifted")
    if matrix.get("contract_id") != "photo-inference-engine" or matrix.get("release") != "v1":
        fail("inference compatibility matrix must target v1")
    if schema.get("compatibility", {}) != {
        "unknown_fields": "ignore",
        "missing_required_fields": "reject",
        "incompatible_change": "new-major-release",
    }:
        fail("inference compatibility policy drifted")
    required_pairs = {
        ("domain-inference-v1", consumer, "forward", "accepted")
        for consumer in ("local-android-v1", "local-ios-v1", "remote-v1", "evaluation-v1")
    }
    pairs = {
        (item.get("producer"), item.get("consumer"), item.get("direction"), item.get("expected"))
        for item in matrix.get("supported_pair", [])
    }
    if pairs != required_pairs:
        fail(f"inference compatibility matrix drifted: {sorted(pairs)}")

    required_provenance = {
        "route", "engineId", "modelVersion", "calibrationVersion", "contractVersion"
    }
    required_candidate = {
        "candidateId", "label", "rank", "confidence", "unresolvedAssumptions", "provenance"
    }

    def validate_provenance(value, label):
        require_keys(value, required_provenance, label)
        if value["route"] not in ROUTES:
            fail(f"{label} has an unknown route")
        for field in ("engineId", "modelVersion", "calibrationVersion"):
            require_nonblank(value[field], f"{label}.{field}")
        if value["contractVersion"] != V1_VERSION:
            fail(f"{label} has an incompatible contract version")

    def validate_candidate(value, label):
        require_keys(value, required_candidate, label)
        require_nonblank(value["candidateId"], f"{label}.candidateId")
        require_nonblank(value["label"], f"{label}.label")
        if not isinstance(value["rank"], int) or isinstance(value["rank"], bool) or value["rank"] <= 0:
            fail(f"{label}.rank must be a positive integer")
        require_keys(value["confidence"], {"score", "band"}, f"{label}.confidence")
        require_number_between(value["confidence"]["score"], 0, 1, f"{label}.confidence.score")
        if value["confidence"]["band"] not in {"low", "medium", "high"}:
            fail(f"{label}.confidence.band is unknown")
        for index, assumption in enumerate(value["unresolvedAssumptions"]):
            require_keys(assumption, {"code", "userMessageKey"}, f"{label}.unresolvedAssumptions[{index}]")
            if assumption["code"] not in {
                "portion-unknown", "preparation-unknown", "hidden-ingredients-unknown", "mixed-dish"
            }:
                fail(f"{label}.unresolvedAssumptions[{index}].code is unknown")
            require_nonblank(assumption["userMessageKey"], f"{label}.unresolvedAssumptions[{index}].userMessageKey")
        validate_provenance(value["provenance"], f"{label}.provenance")

    for name, fixture in release["fixtures"].items():
        if fixture.get("contractVersion") != V1_VERSION:
            fail(f"{name} must use contract-v1")
        reject_forbidden_fields(fixture)
        if name == "capability-ready.json":
            require_keys(fixture, {
                "contractVersion", "state", "reason", "userMessageKey", "engineId", "modelVersion"
            }, name)
            if fixture["state"] not in CAPABILITY_STATES or fixture["reason"] not in CAPABILITY_REASONS:
                fail(f"{name} has an unknown capability state or reason")
            if fixture["state"] != "ready" or fixture["reason"] != "none":
                fail(f"{name} must describe a ready capability with reason none")
            for field in ("userMessageKey", "engineId", "modelVersion"):
                require_nonblank(fixture[field], f"{name}.{field}")
        elif name == "telemetry-event.json":
            require_keys(fixture, {
                "contractVersion", "eventType", "route", "capabilityState", "outcome",
                "durationBucket", "resourceBucket",
            }, name)
            if fixture["eventType"] not in {"attempt", "capability"} or fixture["outcome"] not in {
                "candidates", "abstention", "failure", "cancelled"
            }:
                fail("telemetry fixture has an unknown event type or outcome")
            if fixture["route"] not in ROUTES or fixture["capabilityState"] not in CAPABILITY_STATES:
                fail("telemetry fixture has an unknown route or capability state")
            if fixture["durationBucket"] not in DURATION_BUCKETS or fixture["resourceBucket"] not in RESOURCE_BUCKETS:
                fail("telemetry fixture has an unknown timing or resource bucket")
        else:
            require_keys(fixture, {"contractVersion", "outcome"}, name)
            outcome = fixture["outcome"]
            outcome_type = outcome.get("outcomeType")
            if outcome_type == "candidates":
                require_keys(outcome, {"outcomeType", "candidates"}, f"{name}.outcome")
                candidates = outcome["candidates"]
                if not isinstance(candidates, list) or not candidates:
                    fail(f"{name} must contain at least one candidate")
                for index, candidate in enumerate(candidates):
                    validate_candidate(candidate, f"{name}.candidates[{index}]")
                ranks = [candidate["rank"] for candidate in candidates]
                ids = [candidate["candidateId"] for candidate in candidates]
                if len(set(ranks)) != len(ranks) or sorted(ranks) != list(range(1, len(ranks) + 1)):
                    fail(f"{name} candidate ranks must be unique, contiguous, and start at one")
                if len(set(ids)) != len(ids):
                    fail(f"{name} candidate IDs must be unique")
            elif outcome_type == "abstention":
                require_keys(outcome, {"outcomeType", "reason", "userMessageKey", "provenance"}, f"{name}.outcome")
                if outcome["reason"] not in {
                    "identity-ambiguous", "no-food-detected", "image-quality", "portion-ambiguous", "unsupported-content"
                }:
                    fail(f"{name} has an unknown abstention reason")
                require_nonblank(outcome["userMessageKey"], f"{name}.outcome.userMessageKey")
                validate_provenance(outcome["provenance"], f"{name}.outcome.provenance")
            elif outcome_type == "failure":
                require_keys(outcome, {"outcomeType", "code", "retryable", "userMessageKey", "provenance"}, f"{name}.outcome")
                if outcome["code"] not in {
                    "invalid-input", "model-unavailable", "resource-limit", "temporary-unavailable"
                } or not isinstance(outcome["retryable"], bool):
                    fail(f"{name} has an invalid failure code or retryability")
                require_nonblank(outcome["userMessageKey"], f"{name}.outcome.userMessageKey")
                validate_provenance(outcome["provenance"], f"{name}.outcome.provenance")
            else:
                fail(f"{name} has an unknown outcome type")
    verify_digest(release)


def validate_v2(release):
    expected_fixtures = {
        "capability-ready.json",
        "photo-component-estimated-result.json",
        "photo-component-not-evaluable-result.json",
        "text-component-not-evaluable-result.json",
        "abstention-result.json",
        "failure-result.json",
        "telemetry-event.json",
    }
    if set(release["fixtures"]) != expected_fixtures:
        fail("inference v2 must publish ready, component, abstention, failure, and telemetry fixtures")
    schema = release["schema"]
    matrix = release["matrix"]
    if schema != {
        "schema": "supernutrition.inference-evidence-contract.v2",
        "schema_version": 1,
        "contract_id": "inference-evidence",
        "release": "v2",
        "language_neutral": True,
        "executable": False,
        "canonical_json": True,
        "numeric_nutrition": False,
        "canonical_fixture_kinds": ["capability", "result", "telemetry"],
        "ports": {
            "request": "closed-photo-ref-or-text-plus-immutable-attempt-context",
            "capability": "typed-state-reason-user-safe-message-key-with-model-taxonomy-calibration",
            "result": "visible-component-evidence-or-abstention-or-typed-failure",
            "component": "localized-candidate-calibrated-confidence-visibility-ambiguity-optional-normalized-region",
            "mass": "estimated-finite-ordered-gram-range-or-typed-not-evaluable-or-typed-abstained",
            "provenance": "route-engine-model-taxonomy-calibration-contract",
            "telemetry": "versioned-allowlisted-operational-buckets-without-raw-payload-or-component-values",
        },
        "compatibility": {
            "unknown_fields": "ignore",
            "missing_required_fields": "reject",
            "incompatible_change": "new-major-release",
        },
        "confidence": {
            "low_upper_exclusive": 0.5,
            "medium_upper_exclusive": 0.8,
            "high_lower_inclusive": 0.8,
        },
    }:
        fail("inference v2 schema metadata or port declarations drifted")
    if schema.get("contract_id") != "inference-evidence" or schema.get("release") != "v2":
        fail("inference schema must identify the modality-neutral inference-evidence v2 release")
    if schema.get("language_neutral") is not True or schema.get("executable") is not False:
        fail("inference v2 contract must remain language-neutral data only")
    if schema.get("numeric_nutrition") is not False or schema.get("canonical_fixture_kinds") != [
        "capability", "result", "telemetry"
    ]:
        fail("inference v2 must forbid nutrition and preserve fixture kinds")
    require_keys(matrix, {
        "schema", "contract_id", "release", "digest", "supported_pair", "coexistence_pair"
    }, "inference v2 compatibility matrix")
    if matrix.get("schema") != "supernutrition.inference-evidence-compatibility-matrix.v2" or \
            matrix.get("contract_id") != "inference-evidence" or matrix.get("release") != "v2":
        fail("inference compatibility matrix must target v2")
    # P1-11 — `accepted` asserts a consumer exists and reads this release today.
    # text-v2 and evaluation-v2 were marked accepted and were never built: no
    # TEXT modality exists in commonMain and the evaluation harness has no v2
    # reference. A matrix advertising consumers nobody wrote is worse than an
    # empty one, because it is the document a migration gets planned against.
    required_pairs = {
        ("domain-inference-v2", consumer, "forward", "accepted")
        for consumer in ("local-android-v2", "local-ios-v2")
    } | {
        ("domain-inference-v2", consumer, "forward", "not-yet-implemented")
        for consumer in ("text-v2", "evaluation-v2")
    }
    for index, item in enumerate(matrix.get("supported_pair", [])):
        require_keys(item, {"producer", "consumer", "direction", "expected"},
                     f"inference v2 compatibility matrix.supported_pair[{index}]")
    supported_pairs = [
        (item["producer"], item["consumer"], item["direction"], item["expected"])
        for item in matrix.get("supported_pair", [])
    ]
    if len(supported_pairs) != len(set(supported_pairs)) or set(supported_pairs) != required_pairs:
        fail(f"inference v2 compatibility matrix drifted: {sorted(supported_pairs)}")
    for index, item in enumerate(matrix.get("coexistence_pair", [])):
        require_keys(item, {"first_release", "second_release", "requirement"},
                     f"inference v2 compatibility matrix.coexistence_pair[{index}]")
    coexistence_pairs = [
        (item["first_release"], item["second_release"], item["requirement"])
        for item in matrix.get("coexistence_pair", [])
    ]
    expected_coexistence = {
        (V1_VERSION, V2_VERSION, "separate-ports-required"),
        (V2_VERSION, V1_VERSION, "separate-ports-required"),
    }
    if len(coexistence_pairs) != len(set(coexistence_pairs)) or set(coexistence_pairs) != expected_coexistence:
        fail("inference v1/v2 coexistence matrix drifted")

    def validate_provenance(value, label):
        require_keys(value, {
            "route", "engineId", "modelVersion", "taxonomyVersion", "calibrationVersion", "contractVersion"
        }, label)
        if value["route"] not in ROUTES:
            fail(f"{label} has an unknown route")
        for field in ("engineId", "modelVersion", "taxonomyVersion", "calibrationVersion"):
            require_nonblank(value[field], f"{label}.{field}")
        if value["contractVersion"] != V2_VERSION:
            fail(f"{label} has an incompatible contract version")

    def validate_confidence(value, label):
        require_keys(value, {"score", "band"}, label)
        score = value["score"]
        require_number_between(score, 0, 1, f"{label}.score")
        expected_band = "low" if score < 0.5 else "medium" if score < 0.8 else "high"
        if value["band"] != expected_band:
            fail(f"{label}.band must be calibrated from its score")

    def validate_region(value, label):
        require_keys(value, {"left", "top", "width", "height"}, label)
        for field in ("left", "top", "width", "height"):
            require_number_between(value[field], 0, 1, f"{label}.{field}")
        if value["width"] == 0 or value["height"] == 0:
            fail(f"{label} dimensions must be positive")
        if value["left"] + value["width"] > 1 or value["top"] + value["height"] > 1:
            fail(f"{label} must remain inside normalized bounds")

    def validate_mass(value, label):
        mass_type = value.get("massType") if isinstance(value, dict) else None
        if mass_type == "estimated":
            require_keys(value, {"massType", "range", "calibrationAssumptions"}, label)
            require_keys(value["range"], {"lowerGrams", "upperGrams"}, f"{label}.range")
            lower = value["range"]["lowerGrams"]
            upper = value["range"]["upperGrams"]
            require_number_between(lower, 0, float("inf"), f"{label}.range.lowerGrams")
            require_number_between(upper, 0, float("inf"), f"{label}.range.upperGrams")
            if lower >= upper:
                fail(f"{label}.range must be strictly ordered")
            assumptions = value["calibrationAssumptions"]
            if not isinstance(assumptions, list) or not assumptions or len(set(assumptions)) != len(assumptions):
                fail(f"{label}.calibrationAssumptions must be a nonempty unique list")
            if not set(assumptions) <= {
                "single-view-approximation", "reference-geometry-available", "partial-occlusion"
            }:
                fail(f"{label}.calibrationAssumptions contains an unknown value")
        elif mass_type == "not-evaluable":
            require_keys(value, {"massType", "reason", "userMessageKey"}, label)
            if value["reason"] not in {
                "ambiguous-portion", "mixed-or-occluded", "unsupported-geometry", "missing-calibration",
                "text-has-no-visible-portion",
            }:
                fail(f"{label} has an unknown not-evaluable reason")
            require_identifier(value["userMessageKey"], f"{label}.userMessageKey")
        elif mass_type == "abstained":
            require_keys(value, {"massType", "reason", "userMessageKey"}, label)
            if value["reason"] not in {"identity-ambiguous", "image-quality", "unsupported-content"}:
                fail(f"{label} has an unknown abstained reason")
            require_identifier(value["userMessageKey"], f"{label}.userMessageKey")
        else:
            fail(f"{label} must declare exactly one known mass state")
        return mass_type

    def validate_component(value, label):
        allowed = {
            "componentId", "candidate", "confidence", "visibility", "ambiguity", "region", "mass", "provenance"
        }
        required = allowed - {"region"}
        if not isinstance(value, dict) or set(value) not in (required, allowed):
            fail(f"{label} fields drifted")
        require_identifier(value["componentId"], f"{label}.componentId")
        require_keys(value["candidate"], {"candidateId", "label", "locale"}, f"{label}.candidate")
        require_identifier(value["candidate"]["candidateId"], f"{label}.candidate.candidateId")
        require_nonblank(value["candidate"]["label"], f"{label}.candidate.label")
        require_locale(value["candidate"]["locale"], f"{label}.candidate.locale")
        validate_confidence(value["confidence"], f"{label}.confidence")
        if value["visibility"] not in {"visible", "partially-occluded"}:
            fail(f"{label}.visibility is unknown")
        if value["ambiguity"] not in {"none", "identity-ambiguous", "mixed-content"}:
            fail(f"{label}.ambiguity is unknown")
        if "region" in value:
            validate_region(value["region"], f"{label}.region")
        mass_type = validate_mass(value["mass"], f"{label}.mass")
        validate_provenance(value["provenance"], f"{label}.provenance")
        return mass_type

    for name, fixture in release["fixtures"].items():
        if fixture.get("contractVersion") != V2_VERSION:
            fail(f"{name} must use contract-v2")
        reject_forbidden_fields(fixture)
        if name == "capability-ready.json":
            require_keys(fixture, {
                "contractVersion", "state", "reason", "userMessageKey", "engineId", "modelVersion",
                "taxonomyVersion", "calibrationVersion",
            }, name)
            if fixture["state"] != "ready" or fixture["reason"] != "none":
                fail(f"{name} must describe a ready capability with reason none")
            for field in ("userMessageKey", "engineId"):
                require_identifier(fixture[field], f"{name}.{field}")
            for field in ("modelVersion", "taxonomyVersion", "calibrationVersion"):
                require_nonblank(fixture[field], f"{name}.{field}")
        elif name == "telemetry-event.json":
            require_keys(fixture, {
                "contractVersion", "eventType", "route", "capabilityState", "outcome", "durationBucket",
                "resourceBucket", "componentCountBucket",
            }, name)
            if fixture["eventType"] not in {"attempt", "capability"} or fixture["route"] not in ROUTES:
                fail("v2 telemetry fixture has an unknown event type or route")
            if fixture["capabilityState"] not in CAPABILITY_STATES or fixture["outcome"] not in {
                "components", "abstention", "failure", "cancelled"
            }:
                fail("v2 telemetry fixture has an unknown capability state or outcome")
            if fixture["durationBucket"] not in DURATION_BUCKETS or fixture["resourceBucket"] not in RESOURCE_BUCKETS:
                fail("v2 telemetry fixture has an unknown timing or resource bucket")
            if fixture["componentCountBucket"] not in {"zero", "one", "two-to-four", "five-or-more"}:
                fail("v2 telemetry fixture has an unknown component count bucket")
        elif name in {
            "photo-component-estimated-result.json",
            "photo-component-not-evaluable-result.json",
            "text-component-not-evaluable-result.json",
            "abstention-result.json",
            "failure-result.json",
        }:
            require_keys(fixture, {"contractVersion", "inputModality", "outcome"}, name)
            if fixture["inputModality"] not in {"photo", "text"}:
                fail(f"{name} has an unknown input modality")
            outcome = fixture["outcome"]
            outcome_type = outcome.get("outcomeType") if isinstance(outcome, dict) else None
            if outcome_type == "components":
                require_keys(outcome, {"outcomeType", "components"}, f"{name}.outcome")
                components = outcome["components"]
                if not isinstance(components, list) or not components:
                    fail(f"{name} must contain at least one component")
                ids = []
                mass_types = []
                has_visual_regions = []
                for index, component in enumerate(components):
                    ids.append(component.get("componentId") if isinstance(component, dict) else None)
                    has_visual_regions.append(isinstance(component, dict) and "region" in component)
                    mass_types.append(validate_component(component, f"{name}.components[{index}]"))
                if len(set(ids)) != len(ids):
                    fail(f"{name} component IDs must be unique")
                if fixture["inputModality"] == "text" and any(mass_type != "not-evaluable" for mass_type in mass_types):
                    fail(f"{name} text output must use only not-evaluable mass states")
                if fixture["inputModality"] == "text" and any(has_visual_regions):
                    fail(f"{name} text output must not carry visual region evidence")
                expected_mass = {
                    "photo-component-estimated-result.json": "estimated",
                    "photo-component-not-evaluable-result.json": "not-evaluable",
                    "text-component-not-evaluable-result.json": "not-evaluable",
                }.get(name)
                if expected_mass is not None and set(mass_types) != {expected_mass}:
                    fail(f"{name} does not contain its required mass state")
                if name.startswith("text-") and fixture["inputModality"] != "text":
                    fail(f"{name} must use text input modality")
                if name.startswith("photo-") and fixture["inputModality"] != "photo":
                    fail(f"{name} must use photo input modality")
            elif outcome_type == "abstention":
                if name != "abstention-result.json":
                    fail(f"{name} has an unexpected abstention outcome")
                require_keys(outcome, {"outcomeType", "reason", "userMessageKey", "provenance"}, f"{name}.outcome")
                if outcome["reason"] not in {
                    "identity-ambiguous", "no-food-detected", "image-quality", "unsupported-content"
                }:
                    fail(f"{name} has an unknown abstention reason")
                require_identifier(outcome["userMessageKey"], f"{name}.outcome.userMessageKey")
                validate_provenance(outcome["provenance"], f"{name}.outcome.provenance")
            elif outcome_type == "failure":
                if name != "failure-result.json":
                    fail(f"{name} has an unexpected failure outcome")
                require_keys(outcome, {"outcomeType", "code", "retryable", "userMessageKey", "provenance"}, f"{name}.outcome")
                if outcome["code"] not in {
                    "invalid-input", "model-unavailable", "resource-limit", "temporary-unavailable"
                } or not isinstance(outcome["retryable"], bool):
                    fail(f"{name} has an invalid failure code or retryability")
                require_identifier(outcome["userMessageKey"], f"{name}.outcome.userMessageKey")
                validate_provenance(outcome["provenance"], f"{name}.outcome.provenance")
            else:
                fail(f"{name} has an unknown outcome type")
        else:
            fail(f"inference v2 has an unknown fixture: {name}")
    verify_digest(release)


seen_releases = set()
for root_argument in sys.argv[1:]:
    release = load_release(root_argument)
    contract_id = release["schema"].get("contract_id")
    version = release["schema"].get("release")
    key = (contract_id, version)
    if key in seen_releases:
        fail(f"inference contract release was supplied more than once: {contract_id}/{version}")
    seen_releases.add(key)
    if key == ("photo-inference-engine", "v1"):
        validate_v1(release)
        print("photo-inference-engine contract: OK (v1 ports, canonical evidence, and compatibility matrix)")
    elif key == ("inference-evidence", "v2"):
        validate_v2(release)
        print("inference-evidence contract: OK (v2 component and mass evidence, canonical fixtures, and coexistence matrix)")
    else:
        fail(f"unsupported inference contract release: {contract_id}/{version}")
PY

# Both published releases, from one entrypoint. E7.1 requires this script to
# verify both: two releases coexist during the migration, and a checker that
# covers only the older one would let the newer drift unnoticed while still
# reporting success.
#
# The v2 checks live in their own script rather than being inlined, because the
# two releases have genuinely different rules — v1 has no modality, no spans and
# no capability envelope to check — and one script branching on release would be
# harder to read than two that each verify one thing.
#
# Deliberately not passed the SUPERNUTRITION_INFERENCE_CONTRACT override: that
# variable names a v1 tree, and the negative-fixture harness points it at mutated
# copies. v2 is verified where it actually lives.
"${repo_root}/scripts/verify-inference-v3-contract.sh"
