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

# Rejection for the right reason. `expect_rejected` proves only that the
# verifier said no, and a mutated copy also fails the digest check, so a rule
# that never fired is indistinguishable from one that did. These two close that:
# `repin` recomputes the digest exactly as a release author would, and
# `expect_rejected_because` requires the verifier's own message.
repin() {
    local name="$1"
    python3 - "$work/$name" <<'PY'
from hashlib import sha256
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
matrix_path = root / "compatibility-matrix.toml"
matrix_bytes = re.sub(
    rb'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|pending)"\s*$',
    b'digest = "<release-digest>"',
    matrix_path.read_bytes(),
)
release_files = [
    ("RELEASE.md", (root / "RELEASE.md").read_bytes()),
    ("compatibility-matrix.toml", matrix_bytes),
    ("schema.toml", (root / "schema.toml").read_bytes()),
]
release_files.extend(
    (str(path.relative_to(root)), path.read_bytes())
    for path in sorted((root / "fixtures").glob("*.json"))
)
hash_value = sha256()
for relative, content in sorted(release_files):
    hash_value.update(relative.encode("utf-8"))
    hash_value.update(b"\0")
    hash_value.update(content)
matrix_path.write_bytes(
    re.sub(
        rb'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|pending)"\s*$',
        f'digest = "{hash_value.hexdigest()}"'.encode("utf-8"),
        matrix_path.read_bytes(),
    )
)
PY
}

expect_rejected_because() {
    local label="$1" candidate="$work/$2" pattern="$3" output
    if output=$(SUPERNUTRITION_INFERENCE_V2_CONTRACT="$candidate" "$verifier" 2>&1); then
        echo "negative fixture unexpectedly passed: $label" >&2
        exit 1
    fi
    if ! printf '%s' "$output" | grep -Fq "$pattern"; then
        echo "negative fixture rejected for the wrong reason: $label" >&2
        echo "  expected message containing: $pattern" >&2
        echo "  got: $output" >&2
        exit 1
    fi
}

# The helper is only trustworthy if a re-pinned but otherwise pristine copy
# passes. Without this, every case below could be passing on a stale digest.
seed repin_control
repin repin_control
if ! SUPERNUTRITION_INFERENCE_V2_CONTRACT="$work/repin_control" "$verifier" >/dev/null 2>&1; then
    echo "positive control failed: a re-pinned unmutated v2 copy was rejected" >&2
    exit 1
fi

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

# --- telemetry allowlist and vocabularies -----------------------------------
# Every case below re-pins the digest, because each one must prove that its own
# rule fired rather than that the bytes changed.

mutate_telemetry() {
    local name="$1"
    python3 - "$work/$name/fixtures/telemetry-event.json" "${@:2}" <<'PY'
import json, sys
from pathlib import Path
path = Path(sys.argv[1])
data = json.loads(path.read_text())
for assignment in sys.argv[2:]:
    key, _, value = assignment.partition("=")
    if value == "":
        data.pop(key, None)
    else:
        data[key] = value
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
}

# An unrecognised modality is the case that matters most: the two agreement
# rules are string equality, so a typo would make both False and switch off the
# only structural enforcement of the rule the telemetry section exists for.
seed telemetry_modality
mutate_telemetry telemetry_modality "modality=voice" "route=local"
repin telemetry_modality
expect_rejected_because "telemetry carrying an undeclared modality" telemetry_modality "not a declared modality"

# The rule itself, once the modality is spelled correctly.
seed telemetry_flattened
mutate_telemetry telemetry_flattened "route=local"
repin telemetry_flattened
expect_rejected_because "a text attempt reported as local" telemetry_flattened "flattening"

seed telemetry_photo_text
mutate_telemetry telemetry_photo_text "modality=photo"
repin telemetry_photo_text
expect_rejected_because "a photo attempt reported as local-text" telemetry_photo_text "answered by the text engine"

# An allowlist that admits an extra field is not an allowlist, and free text
# reaches logs one convenient extra at a time.
seed telemetry_extra
mutate_telemetry telemetry_extra "accountId=user-4471"
repin telemetry_extra
expect_rejected_because "telemetry emitting a field outside the allowlist" telemetry_extra "unlisted fields"

seed telemetry_missing
mutate_telemetry telemetry_missing "route="
repin telemetry_missing
expect_rejected_because "telemetry omitting a declared field" telemetry_missing "omits declared fields"

seed telemetry_vocabulary
mutate_telemetry telemetry_vocabulary "durationBucket=over-30s"
repin telemetry_vocabulary
expect_rejected_because "a bucket value outside its declared vocabulary" telemetry_vocabulary "outside its declared vocabulary"

echo "inference v2 negative fixtures: OK"
