#!/usr/bin/env bash
# Read-only schema-to-Kotlin binding check for inference-evidence/v3.
set -euo pipefail

repo_root=$(cd "$(dirname "$BASH_SOURCE")/.." && pwd)
contract_root="$repo_root/contracts/inference/v3"
binding_root="$repo_root/mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference"
if printenv SUPERNUTRITION_INFERENCE_V3_CONTRACT >/dev/null; then
    contract_root=$(printenv SUPERNUTRITION_INFERENCE_V3_CONTRACT)
fi
if printenv SUPERNUTRITION_INFERENCE_V3_BINDING >/dev/null; then
    binding_root=$(printenv SUPERNUTRITION_INFERENCE_V3_BINDING)
fi

python3 - "$contract_root" "$binding_root" <<'PY'
from pathlib import Path
import re
import sys
import tomllib

contract_root = Path(sys.argv[1]).resolve()
binding_root = Path(sys.argv[2]).resolve()
schema = tomllib.loads((contract_root / "schema.toml").read_text(encoding="utf-8"))
text = "\n".join(path.read_text(encoding="utf-8") for path in sorted(binding_root.glob("*.kt")))
if 'INFERENCE_CONTRACT_V3: String = "inference-evidence/v3"' not in text:
    raise SystemExit("v3 binding does not pin inference-evidence/v3")


def enum_body(name):
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


def serial_names(name):
    values = re.findall(r'@SerialName\("([^"]+)"\)', enum_body(name))
    if len(values) != len(set(values)) or not values:
        raise SystemExit(f"{name} has no unique serial-name vocabulary")
    return set(values)


def parameters(name):
    match = re.search(rf"\bdata class {re.escape(name)}\s*\(", text)
    if not match:
        raise SystemExit(f"binding declares no {name}")
    depth = 0
    for index in range(match.end() - 1, len(text)):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                return text[match.end():index]
    raise SystemExit(f"{name} parameter list is not closed")


bindings = {
    "event_name": "TelemetryEventNameV3",
    "route": "InferenceRouteV3",
    "capability_tier": "CapabilityState",
    "duration_bucket": "TelemetryDurationBucketV3",
    "resource_bucket": "TelemetryResourceBucketV3",
    "terminal_outcome": "InferenceV3TelemetryOutcome",
    "item_group_count_bucket": "TelemetryItemGroupCountBucket",
    "component_count_bucket": "TelemetryComponentCountBucket",
}
for key, enum_name in bindings.items():
    declared = set(schema["telemetry"][key])
    emitted = serial_names(enum_name)
    if declared != emitted:
        raise SystemExit(f"{key} vocabulary disagrees: schema={sorted(declared)}, binding={sorted(emitted)}")
if set(schema["modality"]["values"]) != serial_names("InferenceModality"):
    raise SystemExit("modality vocabulary disagrees")
if set(schema["capability"]["state"]) != serial_names("CapabilityState"):
    raise SystemExit("capability state vocabulary disagrees")
if set(schema["capability"]["reason"]) != serial_names("CapabilityReason"):
    raise SystemExit("capability reason vocabulary disagrees")

capability_fields = re.findall(r"\bval\s+([A-Za-z][A-Za-z0-9]*)\s*:", parameters("EngineCapabilityV3"))
if capability_fields != schema["capability"]["emitted_fields"]:
    raise SystemExit(f"capability field list disagrees: {capability_fields}")
if re.search(r"\bval\s+[A-Za-z][A-Za-z0-9]*\s*:\s*[^,\n?]+\?", parameters("EngineCapabilityV3")):
    raise SystemExit("v3 capability unexpectedly declares an optional field")

telemetry_fields = re.findall(r"\bval\s+([A-Za-z][A-Za-z0-9]*)\s*:", parameters("InferenceTelemetryEventV3"))
if telemetry_fields != schema["telemetry"]["emitted_fields"]:
    raise SystemExit(f"telemetry field list disagrees: {telemetry_fields}")
for required in (
    "sealed interface InferenceRequestV3",
    "data class Photo",
    "class Text",
    "data class Components",
    "data class Candidates",
    "InferenceSourceSpan",
    "InferenceV3MassEvidence",
):
    if required not in text:
        raise SystemExit(f"v3 binding is missing {required}")
print(f"inference-evidence v3 binding: OK ({len(capability_fields)} capability fields, {len(telemetry_fields)} telemetry fields)")
PY
