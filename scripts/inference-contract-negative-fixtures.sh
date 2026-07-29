#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
fixture_root=$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-inference-contract.XXXXXX")
trap 'rm -rf "$fixture_root"' EXIT
cp -R "${repo_root}/contracts/inference/v1" "$fixture_root/v1"

python3 - "$fixture_root/v1" <<'PY'
from pathlib import Path
import sys

root = Path(sys.argv[1])
matrix = root / "compatibility-matrix.toml"
matrix.write_text(matrix.read_text().replace('expected = "accepted"', 'expected = "rejected"', 1))
PY

if SUPERNUTRITION_INFERENCE_CONTRACT="$fixture_root/v1" "${repo_root}/scripts/verify-inference-contract.sh" >/dev/null 2>&1; then
    echo "negative fixture unexpectedly passed: incompatible inference pair" >&2
    exit 1
fi

cp -R "${repo_root}/contracts/inference/v1" "$fixture_root/nutrition"
python3 - "$fixture_root/nutrition/fixtures/candidate-result.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["candidates"][0]["calories"] = 42
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY

if SUPERNUTRITION_INFERENCE_CONTRACT="$fixture_root/nutrition" "${repo_root}/scripts/verify-inference-contract.sh" >/dev/null 2>&1; then
    echo "negative fixture unexpectedly passed: numeric nutrition" >&2
    exit 1
fi

expect_rejected() {
    local label="$1"
    local root="$2"
    if SUPERNUTRITION_INFERENCE_CONTRACT="$root" "${repo_root}/scripts/verify-inference-contract.sh" >/dev/null 2>&1; then
        echo "negative fixture unexpectedly passed: $label" >&2
        exit 1
    fi
}

cp -R "${repo_root}/contracts/inference/v1" "$fixture_root/duplicate-keys"
python3 - "$fixture_root/duplicate-keys/fixtures/candidate-result.json" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text()
needle = '"contractVersion":"photo-inference-engine/v1",'
path.write_text(text.replace(needle, needle + '"contractVersion":"photo-inference-engine/v1",', 1))
PY
expect_rejected "duplicate JSON keys" "$fixture_root/duplicate-keys"

cp -R "${repo_root}/contracts/inference/v1" "$fixture_root/nonfinite"
python3 - "$fixture_root/nonfinite/fixtures/candidate-result.json" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
path.write_text(path.read_text().replace('"score":0.93', '"score":NaN', 1))
PY
expect_rejected "non-finite confidence" "$fixture_root/nonfinite"

cp -R "${repo_root}/contracts/inference/v1" "$fixture_root/unknown-outcome"
python3 - "$fixture_root/unknown-outcome/fixtures/candidate-result.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["outcomeType"] = "unsupported"
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "unknown outcome type" "$fixture_root/unknown-outcome"

cp -R "${repo_root}/contracts/inference/v1" "$fixture_root/noncontiguous-rank"
python3 - "$fixture_root/noncontiguous-rank/fixtures/candidate-result.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["candidates"][0]["rank"] = 2
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "non-contiguous candidate rank" "$fixture_root/noncontiguous-rank"

cp -R "${repo_root}/contracts/inference/v1" "$fixture_root/duplicate-candidate-id"
python3 - "$fixture_root/duplicate-candidate-id/fixtures/candidate-result.json" <<'PY'
from pathlib import Path
import copy
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
candidate = copy.deepcopy(data["outcome"]["candidates"][0])
candidate["rank"] = 2
data["outcome"]["candidates"].append(candidate)
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "duplicate candidate ID" "$fixture_root/duplicate-candidate-id"

cp -R "${repo_root}/contracts/inference/v1" "$fixture_root/blank-message-key"
python3 - "$fixture_root/blank-message-key/fixtures/abstention-result.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["outcome"]["userMessageKey"] = " "
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "blank outcome message key" "$fixture_root/blank-message-key"

cp -R "${repo_root}/contracts/inference/v1" "$fixture_root/ready-capability"
python3 - "$fixture_root/ready-capability/fixtures/capability-ready.json" <<'PY'
from pathlib import Path
import json
import sys

path = Path(sys.argv[1])
data = json.loads(path.read_text())
data["reason"] = "model-missing"
path.write_text(json.dumps(data, separators=(",", ":")) + "\n")
PY
expect_rejected "ready capability with unavailable reason" "$fixture_root/ready-capability"

echo "inference contract negative fixtures: OK"
