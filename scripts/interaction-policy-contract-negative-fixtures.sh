#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
fixture_root=$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-interaction-policy.XXXXXX")
trap 'rm -rf "$fixture_root"' EXIT
cp -R "${repo_root}/contracts/interaction-policy/v1" "$fixture_root/v1"

python3 - "$fixture_root/v1" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1]) / "schema.toml"
path.write_text(path.read_text().replace('standard_serving_requires = "canonical-item-confirmed"', 'standard_serving_requires = "locale-derived"'))
PY

if SUPERNUTRITION_INTERACTION_POLICY_CONTRACT="$fixture_root/v1" "${repo_root}/scripts/verify-interaction-policy-contract.sh" >/dev/null 2>&1; then
    echo "negative fixture unexpectedly passed: serving confirmation bypass" >&2
    exit 1
fi

echo "interaction-policy contract negative fixtures: OK"
