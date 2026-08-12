#!/usr/bin/env bash
# Negative fixtures for the v3 language-neutral contract verifier.
set -euo pipefail

repo_root=$(cd "$(dirname "$BASH_SOURCE")/.." && pwd)
verifier="$repo_root/scripts/verify-inference-v3-contract.sh"
tmp_dir=$(printenv TMPDIR || true)
if test -z "$tmp_dir"; then
    tmp_dir=/tmp
fi
work=$(mktemp -d "$tmp_dir/supernutrition-inference-v3.XXXXXX")
trap 'rm -rf "$work"' EXIT

seed() {
    local name="$1"
    cp -R "$repo_root/contracts/inference/v3" "$work/$name"
}

repin() {
    python3 - "$1" <<'PY'
from hashlib import sha256
from pathlib import Path
import re
import sys

root = Path(sys.argv[1])
matrix = root / "compatibility-matrix.toml"
normalized = re.sub(
    rb'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|pending|<release-digest>)"\s*$',
    b'digest = "<release-digest>"',
    matrix.read_bytes(),
)
files = [("compatibility-matrix.toml", normalized)]
files.extend(
    (str(path.relative_to(root)), path.read_bytes())
    for path in sorted(root.rglob("*"))
    if path.is_file() and path != matrix
)
digest = sha256()
for relative, content in sorted(files):
    digest.update(relative.encode())
    digest.update(b"\0")
    digest.update(content)
matrix.write_text(
    re.sub(
        r'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|pending|<release-digest>)"\s*$',
        f'digest = "{digest.hexdigest()}"',
        matrix.read_text(),
    )
)
PY
}

expect_rejected() {
    local label="$1" root="$2"
    if SUPERNUTRITION_INFERENCE_V3_CONTRACT="$root" "$verifier" >/dev/null 2>&1; then
        echo "negative fixture unexpectedly passed: $label" >&2
        exit 1
    fi
}

if ! "$verifier" >/dev/null; then
    echo "v3 positive control failed" >&2
    exit 1
fi

seed span
python3 - "$work/span/fixtures/text-candidate-result.json" <<'PY'
import json
from pathlib import Path
import sys
path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["itemGroups"][0]["sourceSpan"]["end"] = 999
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
repin "$work/span"
expect_rejected "text source span outside the description" "$work/span"

seed mass
python3 - "$work/mass/fixtures/photo-component-estimated-result.json" <<'PY'
import json
from pathlib import Path
import sys
path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["components"][0]["mass"]["range"]["upperGrams"] = 0
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
repin "$work/mass"
expect_rejected "degenerate photo gram range" "$work/mass"

seed telemetry
python3 - "$work/telemetry/fixtures/telemetry-event.json" <<'PY'
import json
from pathlib import Path
import sys
path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["description"] = "user text must not enter telemetry"
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
repin "$work/telemetry"
expect_rejected "free text in telemetry" "$work/telemetry"

seed nutrition
python3 - "$work/nutrition/fixtures/photo-component-estimated-result.json" <<'PY'
import json
from pathlib import Path
import sys
path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["components"][0]["calories"] = 42
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
repin "$work/nutrition"
expect_rejected "numeric nutrition in photo evidence" "$work/nutrition"

echo "inference v3 negative fixtures: OK"
