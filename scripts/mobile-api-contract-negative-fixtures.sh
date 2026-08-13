#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
fixture_root=$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-mobile-api-contract.XXXXXX")
trap 'rm -rf "$fixture_root"' EXIT
cp -R "${repo_root}/contracts/mobile-api/v1" "$fixture_root/v1"

python3 - "$fixture_root/v1" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1]) / "compatibility-matrix.toml"
path.write_text(path.read_text().replace('expected = "accepted"', 'expected = "rejected"', 1))
PY

if SUPERNUTRITION_MOBILE_API_CONTRACT="$fixture_root/v1" "${repo_root}/scripts/verify-mobile-api-contract.sh" >/dev/null 2>&1; then
    echo "negative fixture unexpectedly passed: incompatible skew" >&2
    exit 1
fi

echo "mobile-api contract negative fixtures: OK"
