#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
validator="$repo_root/scripts/verify-ci-policy.sh"
workflow="$repo_root/.github/workflows/ci-required.yml"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-ci-policy.XXXXXX")"
trap 'rm -rf "$fixture_root"' EXIT

expect_rejected() {
  local name="$1"
  local fixture="$2"
  if SUPERNUTRITION_CI_WORKFLOW="$fixture" "$validator" >"$fixture_root/$name.out" 2>&1; then
    printf 'CI policy fixture unexpectedly accepted: %s\n' "$name" >&2
    cat "$fixture_root/$name.out" >&2
    exit 1
  fi
}

cp "$workflow" "$fixture_root/path-filter.yml"
python3 - "$fixture_root/path-filter.yml" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
path.write_text(text.replace("  pull_request:\n", "  pull_request:\n    paths:\n      - '**'\n", 1), encoding="utf-8")
PY
expect_rejected path-filter "$fixture_root/path-filter.yml"

cp "$workflow" "$fixture_root/extra-need.yml"
python3 - "$fixture_root/extra-need.yml" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
needle = "jobs:\n"
replacement = "jobs:\n  unexpected-job:\n    runs-on: ubuntu-latest\n    steps: []\n"
path.write_text(text.replace(needle, replacement, 1), encoding="utf-8")
PY
expect_rejected extra-need "$fixture_root/extra-need.yml"

cp "$workflow" "$fixture_root/missing-job.yml"
python3 - "$fixture_root/missing-job.yml" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
path.write_text(text.replace("  mobile-unit-coverage:\n", "", 1), encoding="utf-8")
PY
expect_rejected missing-job "$fixture_root/missing-job.yml"

cp "$workflow" "$fixture_root/missing-fingerprint.yml"
python3 - "$fixture_root/missing-fingerprint.yml" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
path.write_text(text.replace("mobileUnitCoverage", "missingMobileCoverage", 1), encoding="utf-8")
PY
expect_rejected missing-fingerprint "$fixture_root/missing-fingerprint.yml"

printf '%s\n' 'CI policy negative fixtures: OK'
