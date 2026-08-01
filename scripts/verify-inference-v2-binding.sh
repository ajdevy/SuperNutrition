#!/usr/bin/env bash
# Read-only check that the Kotlin binding emits the wire values v2 declares.
#
# The contract verifier proves the release is internally consistent, and it
# deliberately refuses to look at source: a language-neutral release must be
# checkable without a Kotlin toolchain. That leaves a gap nothing else covers.
# The canonical fixture pins one value per vocabulary, so seventeen of the
# twenty-four declared telemetry values were pinned in neither direction, and a
# rename on either side would have been invisible to every gate — the exact
# second-source-of-truth drift AGENTS.md records as having twice reached users.
#
# So this compares the two directly, in both directions. A schema value with no
# @SerialName is a vocabulary nothing can emit; an @SerialName absent from the
# schema is a value the wire never agreed to.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
contract_root="${SUPERNUTRITION_INFERENCE_V2_CONTRACT:-${repo_root}/contracts/inference/v2}"
binding_root="${SUPERNUTRITION_INFERENCE_V2_BINDING:-${repo_root}/mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference}"

python3 - "$contract_root" "$binding_root" <<'PY'
from pathlib import Path
import re
import sys
import tomllib

contract_root = Path(sys.argv[1]).resolve()
binding_root = Path(sys.argv[2]).resolve()

schema_path = contract_root / "schema.toml"
if not schema_path.is_file():
    raise SystemExit(f"missing inference v2 schema: {schema_path}")
sources = sorted(binding_root.glob("*.kt"))
if not sources:
    raise SystemExit(f"no Kotlin binding sources under {binding_root}")

schema = tomllib.loads(schema_path.read_text())
telemetry = schema.get("telemetry", {})
if not telemetry:
    raise SystemExit("v2 schema declares no [telemetry] section for the binding to satisfy")

text = "\n".join(path.read_text() for path in sources)


def enum_body(name):
    """The source between an enum's braces, nested braces included.

    Enum entries can be followed by a companion object, so a non-greedy match to
    the first closing brace would silently truncate the vocabulary and report a
    value as missing when it is present.
    """
    match = re.search(rf"\benum class {re.escape(name)}\b[^{{]*{{", text)
    if not match:
        raise SystemExit(f"binding declares no enum class {name}")
    depth = 0
    for index in range(match.end() - 1, len(text)):
        if text[index] == "{":
            depth += 1
        elif text[index] == "}":
            depth -= 1
            if depth == 0:
                return text[match.end():index]
    raise SystemExit(f"enum class {name} is not closed")


def serial_names(enum_name):
    found = re.findall(r'@SerialName\("([^"]*)"\)', enum_body(enum_name))
    if not found:
        raise SystemExit(f"enum class {enum_name} declares no @SerialName wire values")
    duplicates = {value for value in found if found.count(value) > 1}
    if duplicates:
        raise SystemExit(f"enum class {enum_name} declares {sorted(duplicates)} more than once")
    return set(found)


# Every vocabulary the contract declares, and the enum that has to produce it.
BINDINGS = {
    "event_name": "TelemetryEventNameV2",
    "route": "InferenceRouteV2",
    "capability_tier": "CapabilityState",
    "duration_bucket": "TelemetryDurationBucketV2",
    "resource_bucket": "TelemetryResourceBucketV2",
    "terminal_outcome": "InferenceTelemetryOutcome",
    "item_group_count_bucket": "TelemetryItemGroupCountBucket",
}

for key, enum_name in BINDINGS.items():
    declared = set(telemetry.get(key, []))
    if not declared:
        raise SystemExit(f"v2 schema declares no {key} vocabulary")
    emitted = serial_names(enum_name)
    unemittable = declared - emitted
    if unemittable:
        raise SystemExit(
            f"{key} declares {sorted(unemittable)}, which {enum_name} cannot emit: "
            "a vocabulary nothing emits is a claim, not a contract"
        )
    unagreed = emitted - declared
    if unagreed:
        raise SystemExit(
            f"{enum_name} emits {sorted(unagreed)}, absent from the {key} vocabulary: "
            "the wire never agreed to that value"
        )

# The modality discriminator is checked against the release's own list rather
# than a telemetry-local copy, because the telemetry event reuses it.
declared_modalities = set(schema.get("modality", {}).get("values", []))
emitted_modalities = serial_names("InferenceModality")
if declared_modalities != emitted_modalities:
    raise SystemExit(
        f"modality vocabulary disagrees: schema {sorted(declared_modalities)}, "
        f"binding {sorted(emitted_modalities)}"
    )

# The field list, not only the values. An allowlist is only as closed as the
# type that produces it, and a property added to the event would otherwise be
# emitted without appearing in emitted_fields.
event_match = re.search(r"\bdata class InferenceTelemetryEventV2\s*\(", text)
if not event_match:
    raise SystemExit("binding declares no InferenceTelemetryEventV2")
depth = 0
for index in range(event_match.end() - 1, len(text)):
    if text[index] == "(":
        depth += 1
    elif text[index] == ")":
        depth -= 1
        if depth == 0:
            parameters = text[event_match.end():index]
            break
else:
    raise SystemExit("InferenceTelemetryEventV2's parameter list is not closed")

emitted_fields = re.findall(r"\bval\s+([A-Za-z][A-Za-z0-9]*)\s*:", parameters)
declared_fields = telemetry.get("emitted_fields", [])
if not declared_fields:
    raise SystemExit("v2 schema declares no telemetry emitted_fields")
if emitted_fields != declared_fields:
    raise SystemExit(
        "telemetry field list disagrees between schema and binding:\n"
        f"  schema:  {declared_fields}\n"
        f"  binding: {emitted_fields}\n"
        "Order matters: canonical_json is true, so a reordered field list changes the bytes."
    )

print(f"inference-engine v2 binding: OK ({len(declared_fields)} fields, {len(BINDINGS) + 1} vocabularies)")
PY
