#!/usr/bin/env bash
# Negative fixtures for inference-engine v2.
#
# v2 exists to carry a modality, so these cover the properties v1 could not
# express. A verifier that only passes on the happy path proves nothing about
# the rules it claims to enforce.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
verifier="${repo_root}/scripts/verify-inference-v2-contract.sh"

if test ! -x "$verifier"; then
    echo "inference v2 verifier is missing or not executable: $verifier" >&2
    exit 1
fi

work=$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-inference-v2.XXXXXX")
trap 'rm -rf "$work"' EXIT

# Each case gets a pristine copy, mutates one thing, and must be rejected.
expect_rejected() {
    local label="$1" candidate="$work/$2"
    if SUPERNUTRITION_INFERENCE_V2_CONTRACT="$candidate" "$verifier" >/dev/null 2>&1; then
        echo "negative fixture unexpectedly passed: $label" >&2
        exit 1
    fi
}

seed() {
    local name="$1"
    cp -R "${repo_root}/contracts/inference/v2" "$work/$name"
}

# Positive control first: an unmutated copy must pass, or every rejection below
# proves only that the copy is broken.
seed baseline
if ! SUPERNUTRITION_INFERENCE_V2_CONTRACT="$work/baseline" "$verifier" >/dev/null 2>&1; then
    echo "positive control failed: an unmutated v2 copy was rejected" >&2
    exit 1
fi

# --- both payloads present --------------------------------------------------
seed both
python3 - "$work/both/fixtures/request-text.json" <<'PY'
import json, sys
from pathlib import Path
path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["photo"] = {"photoRefId": "photo-ref-smuggled"}
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "request carrying both a photo and a text payload" both

# --- neither payload present ------------------------------------------------
seed neither
python3 - "$work/neither/fixtures/request-photo.json" <<'PY'
import json, sys
from pathlib import Path
path = Path(sys.argv[1])
data = json.loads(path.read_text())
data.pop("photo")
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "request carrying no modality payload" neither

# --- discriminator disagrees with the payload -------------------------------
seed mismatch
python3 - "$work/mismatch/fixtures/request-text.json" <<'PY'
import json, sys
from pathlib import Path
path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["modality"] = "photo"
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "modality discriminator disagreeing with the payload present" mismatch

# --- source span that does not index the description ------------------------
# The span is what makes a proposed split editable with visible provenance. A
# span pointing at the wrong text is worse than none: it looks authoritative.
seed span
python3 - "$work/span/fixtures/candidate-result-text.json" <<'PY'
import json, sys
from pathlib import Path
path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["itemGroups"][0]["sourceSpan"] = {"start": 0, "end": 999}
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "item span running past the end of the description" span

# --- numeric nutrition smuggled into a result -------------------------------
# The rule every modality shares, and the one the whole grounding chain rests on.
seed nutrition
python3 - "$work/nutrition/fixtures/candidate-result-text.json" <<'PY'
import json, sys
from pathlib import Path
path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["itemGroups"][0]["candidates"][0]["calories"] = 78
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "candidate carrying numeric nutrition" nutrition

# --- free text leaking into telemetry ---------------------------------------
seed telemetry
python3 - "$work/telemetry/fixtures/telemetry-event.json" <<'PY'
import json, sys
from pathlib import Path
path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["description"] = "two boiled eggs and a slice of rye toast"
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "telemetry carrying a user-authored description" telemetry

# --- contract id reverted to the photo-shaped name --------------------------
seed rename
python3 - "$work/rename/schema.toml" <<'PY'
import sys
from pathlib import Path
path = Path(sys.argv[1])
path.write_text(path.read_text().replace(
    'contract_id = "inference-engine"', 'contract_id = "photo-inference-engine"', 1))
PY
expect_rejected "contract id reverted to the photo-shaped name" rename

echo "inference v2 negative fixtures: OK"
