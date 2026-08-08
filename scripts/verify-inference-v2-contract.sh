#!/usr/bin/env bash
# Read-only validation of the inference-engine v2 release.
#
# v2 exists to carry an identification modality, so the checks that matter most
# are the ones v1 could not express: exactly one payload per request, a
# modality-neutral contract id, per-item grouping with source spans that index
# the description they claim to describe, and free text excluded from telemetry.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
contract_root="${SUPERNUTRITION_INFERENCE_V2_CONTRACT:-${repo_root}/contracts/inference/v2}"

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
        raise SystemExit(f"missing inference v2 contract file: {path}")

expected_fixtures = {
    "abstention-result.json",
    "candidate-result-photo.json",
    "candidate-result-text.json",
    "capability-ready.json",
    "failure-result.json",
    "request-photo.json",
    "request-text.json",
    "telemetry-event.json",
}
if {path.name for path in fixture_paths} != expected_fixtures:
    raise SystemExit("inference v2 fixture set drifted; both modalities must stay covered")

schema = tomllib.loads(schema_path.read_text())
matrix = tomllib.loads(matrix_path.read_text())

# The rename is the reason this is a major release rather than an additive
# change, so it is asserted rather than assumed.
if schema.get("contract_id") != "inference-engine" or schema.get("release") != "v2":
    raise SystemExit("v2 schema must identify the modality-neutral inference-engine v2 release")
if schema.get("previous_release") != "photo-inference-engine/v1":
    raise SystemExit("v2 must record the release it supersedes")
if schema.get("language_neutral") is not True or schema.get("executable") is not False:
    raise SystemExit("inference contract must remain language-neutral data only")
if schema.get("numeric_nutrition") is not False:
    raise SystemExit("inference contract must forbid numeric nutrition")
if schema.get("canonical_fixture_kinds") != ["request", "capability", "result", "telemetry"]:
    raise SystemExit("v2 fixture kinds drifted; request is a canonical kind because modality lives there")
if matrix.get("contract_id") != "inference-engine" or matrix.get("release") != "v2":
    raise SystemExit("v2 compatibility matrix must target the v2 release")

compatibility = schema.get("compatibility", {})
if compatibility.get("incompatible_change") != "new-major-release":
    raise SystemExit("v2 must keep requiring a new major release for an incompatible change")
if compatibility.get("coexists_with") != "photo-inference-engine/v1":
    raise SystemExit("v2 must declare that v1 remains published during migration")

modality = schema.get("modality", {})
if sorted(modality.get("values", [])) != ["photo", "text"]:
    raise SystemExit("v2 must declare exactly the photo and text modalities")
if modality.get("cardinality") != "exactly-one" or modality.get("neither_or_both") != "reject":
    raise SystemExit("v2 must require exactly one payload and reject neither-or-both")
if modality.get("text", {}).get("input_locale") != "required":
    raise SystemExit("text modality must carry its input locale; catalog country is not language")
if modality.get("text", {}).get("loggable") is not False:
    raise SystemExit("free text must not be loggable")
if modality.get("text", {}).get("telemetry_eligible") is not False:
    raise SystemExit("free text must not be telemetry eligible")

segmentation = schema.get("result", {}).get("segmentation", {})
if segmentation.get("source_span") != "required":
    raise SystemExit("text results must carry the description span each item derived from")
if segmentation.get("partial_segmentation") != "reject":
    raise SystemExit("a partially segmented description must abstain, not return a confident subset")

fixtures = {path.name: json.loads(path.read_text()) for path in fixture_paths}

# Exactly one payload, per fixture, checked against the payload keys actually
# present rather than against the declared modality alone.
for name in ("request-photo.json", "request-text.json"):
    request = fixtures[name]
    present = {key for key in ("photo", "text") if key in request}
    if len(present) != 1:
        raise SystemExit(f"{name} must carry exactly one modality payload, found {sorted(present)}")
    if request.get("modality") not in present:
        raise SystemExit(f"{name} modality discriminator disagrees with its payload")
if "inputLocale" not in fixtures["request-text.json"].get("text", {}):
    raise SystemExit("text request fixture must carry inputLocale")

# The span offsets must index the description they claim to describe. A fixture
# whose spans are merely plausible is reference data that teaches the wrong shape.
description = fixtures["request-text.json"]["text"]["description"]
groups = fixtures["candidate-result-text.json"]["outcome"]["itemGroups"]
if len(groups) < 2:
    raise SystemExit("text result fixture must show more than one detected item")
for group in groups:
    span = group.get("sourceSpan")
    if not span:
        raise SystemExit(f"item group {group.get('groupId')} is missing its sourceSpan")
    start, end = span.get("start"), span.get("end")
    if not isinstance(start, int) or not isinstance(end, int) or not 0 <= start < end <= len(description):
        raise SystemExit(f"item group {group.get('groupId')} span does not index the description")
    if not description[start:end].strip() or description[start:end] != description[start:end].strip():
        raise SystemExit(f"item group {group.get('groupId')} span has stray whitespace at its edges")

# No modality may return numeric nutrition. Checked structurally rather than by
# reading the schema flag, because the flag is a claim and this is the property.
FORBIDDEN = {"calories", "kcal", "protein", "carbohydrates", "fat", "macros", "nutrients"}
def scan(node, origin):
    if isinstance(node, dict):
        for key, value in node.items():
            if key.lower() in FORBIDDEN:
                raise SystemExit(f"{origin} carries numeric nutrition field '{key}'")
            scan(value, origin)
    elif isinstance(node, list):
        for item in node:
            scan(item, origin)
for name, payload in fixtures.items():
    scan(payload, name)

# The capability fixture was checked only for its filename, so it could declare
# any shape at all. It is a canonical fixture: a binding is written from it.
capability_schema = schema.get("capability", {})
capability = fixtures["capability-ready.json"]
capability_fields = capability_schema.get("emitted_fields", [])
if not capability_fields:
    raise SystemExit("v2 must declare the fields a capability report carries")
if capability_schema.get("unlisted_field") != "reject":
    raise SystemExit("an allowlist that admits unlisted fields is not an allowlist")
optional_capability_fields = set(capability_schema.get("optional_fields", []))
if set(capability) - set(capability_fields):
    raise SystemExit(f"capability fixture carries unlisted fields: {sorted(set(capability) - set(capability_fields))}")
missing = set(capability_fields) - set(capability) - optional_capability_fields
if missing:
    raise SystemExit(f"capability fixture omits required fields: {sorted(missing)}")
if capability["modality"] not in modality.get("values", []):
    raise SystemExit(f"capability fixture modality={capability['modality']!r} is not a declared modality")
for field in ("state", "reason"):
    values = capability_schema.get(field, [])
    if not values:
        raise SystemExit(f"v2 must declare the capability {field} vocabulary")
    if capability[field] not in values:
        raise SystemExit(f"capability fixture {field}={capability[field]!r} is outside its declared vocabulary")

# The self-contradicting reports, checked structurally rather than trusted to the
# binding. A ready capability with an unavailable reason, or an unavailable one
# claiming none, says two things at once and a surface has to pick.
if capability_schema.get("ready_requires_reason") != "none":
    raise SystemExit("a ready capability must not report an unavailable reason")
if capability_schema.get("unavailable_forbids_reason") != "none":
    raise SystemExit("an unavailable capability must report why")
if capability_schema.get("ready_requires_model_version") is not True:
    raise SystemExit("a ready capability must name the model it is ready with")
if capability["state"] == "ready":
    if capability["reason"] != "none":
        raise SystemExit("capability fixture is ready and still reports an unavailable reason")
    if not capability.get("modelVersion"):
        raise SystemExit("capability fixture is ready and names no model version")
elif capability["reason"] == "none":
    raise SystemExit("capability fixture is unavailable and reports no reason")

# Telemetry must not carry the description or any free text.
telemetry = fixtures["telemetry-event.json"]
if "description" in json.dumps(telemetry):
    raise SystemExit("telemetry fixture must not carry a user-authored description")

# The telemetry allowlist is checked against the fixture rather than merely
# declared. A field list nothing validates drifts from the shape actually
# emitted, and the drift is invisible until a description shows up in a log.
telemetry_schema = schema.get("telemetry", {})
allowed_fields = telemetry_schema.get("emitted_fields", [])
if not allowed_fields:
    raise SystemExit("v2 must declare the telemetry fields a producer may emit")
if telemetry_schema.get("unlisted_field") != "reject":
    raise SystemExit("an allowlist that admits unlisted fields is not an allowlist")
if telemetry_schema.get("raw_payload") != "forbidden":
    raise SystemExit("telemetry must forbid raw payloads")
if set(telemetry) - set(allowed_fields):
    raise SystemExit(f"telemetry fixture emits unlisted fields: {sorted(set(telemetry) - set(allowed_fields))}")
if set(allowed_fields) - set(telemetry):
    raise SystemExit(f"telemetry fixture omits declared fields: {sorted(set(allowed_fields) - set(telemetry))}")

# Every vocabulary is checked against the value the fixture actually carries, so
# a binding written from the fixture and a binding written from the schema
# cannot disagree about what the wire value is.
VOCABULARIES = {
    "eventName": "event_name",
    "route": "route",
    "capabilityTier": "capability_tier",
    "durationBucket": "duration_bucket",
    "resourceBucket": "resource_bucket",
    "terminalOutcome": "terminal_outcome",
    "itemGroupCountBucket": "item_group_count_bucket",
}
for field, key in VOCABULARIES.items():
    values = telemetry_schema.get(key, [])
    if not values:
        raise SystemExit(f"v2 must declare the {field} vocabulary")
    if telemetry[field] not in values:
        raise SystemExit(f"telemetry fixture {field}={telemetry[field]!r} is outside its declared vocabulary")

# Modality is validated against the release's own modality list rather than a
# telemetry-local copy. It is checked at all because the two agreement rules
# below are string equality: an unrecognised modality makes both of them False,
# which silently disables the only structural enforcement of the rule this
# section exists for. A guard that a typo can switch off is not a guard.
if telemetry["modality"] not in modality.get("values", []):
    raise SystemExit(f"telemetry fixture modality={telemetry['modality']!r} is not a declared modality")

# The route a modality cannot have produced. Asserted structurally because the
# text case is the whole reason `local-text` exists: a text attempt reported as
# `local` is indistinguishable in a log from an on-device photo attempt.
if telemetry_schema.get("modality_route_agreement") != "reject-mismatch":
    raise SystemExit("telemetry must reject a route its modality cannot have produced")
if telemetry["modality"] == "text" and telemetry["route"] == "local":
    raise SystemExit("a text attempt reported as 'local' is the flattening local-text exists to prevent")
if telemetry["modality"] == "photo" and telemetry["route"] == "local-text":
    raise SystemExit("a photo attempt cannot have been answered by the text engine")

matrix_bytes = re.sub(
    rb'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|pending)"\s*$',
    b'digest = "<release-digest>"',
    matrix_path.read_bytes(),
)
hash_value = sha256()
release_files = [
    ("RELEASE.md", release_path.read_bytes()),
    ("compatibility-matrix.toml", matrix_bytes),
    ("schema.toml", schema_path.read_bytes()),
]
release_files.extend((str(path.relative_to(root)), path.read_bytes()) for path in fixture_paths)
for relative, content in sorted(release_files):
    hash_value.update(relative.encode("utf-8"))
    hash_value.update(b"\0")
    hash_value.update(content)
if matrix.get("digest") != hash_value.hexdigest():
    raise SystemExit(
        f"inference v2 digest is stale: recorded {matrix.get('digest')}, computed {hash_value.hexdigest()}"
    )

allowed = {
    "RELEASE.md",
    "schema.toml",
    "compatibility-matrix.toml",
    *(f"fixtures/{name}" for name in expected_fixtures),
}
entries = list(root.rglob("*"))
if any(path.is_symlink() for path in entries) or any(
    str(path.relative_to(root)) not in allowed for path in entries if path.is_file()
):
    raise SystemExit("inference v2 release contains executable or source material")

print("inference-engine contract: OK (v2 modality, both payloads, spans, and compatibility matrix)")
PY
