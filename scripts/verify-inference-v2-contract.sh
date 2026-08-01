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

# Telemetry must not carry the description or any free text.
telemetry = fixtures["telemetry-event.json"]
if "description" in json.dumps(telemetry):
    raise SystemExit("telemetry fixture must not carry a user-authored description")

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
