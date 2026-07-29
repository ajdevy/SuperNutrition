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

echo "inference contract negative fixtures: OK"
