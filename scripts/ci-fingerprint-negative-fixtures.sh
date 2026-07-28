#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-ci-fingerprint.XXXXXX")"
trap 'rm -rf "$fixture_root"' EXIT

jobs=(quality-linux ui-journeys-android quality-apple ui-journeys-ios quality-web-landing quality-backend)
for job in "${jobs[@]}"; do
  "$repo_root/scripts/emit-ci-fingerprint.sh" "$job" "$fixture_root/$job.fingerprint"
done

SUPERNUTRITION_CI_FINGERPRINT_ROOT="$fixture_root" \
SUPERNUTRITION_EXPECTED_SHA="$(git -C "$repo_root" rev-parse HEAD)" \
  "$repo_root/scripts/verify-ci-fingerprints.sh" >/dev/null

expected_sha="$(git -C "$repo_root" rev-parse HEAD)"
python3 - "$fixture_root/quality-linux.fingerprint" "$expected_sha" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
expected_sha = sys.argv[2]
path.write_text(
    path.read_text(encoding="utf-8").replace(
        f"commit_sha={expected_sha}",
        "commit_sha=0000000000000000000000000000000000000000",
    ),
    encoding="utf-8",
)
PY
if SUPERNUTRITION_CI_FINGERPRINT_ROOT="$fixture_root" \
  SUPERNUTRITION_EXPECTED_SHA="$expected_sha" \
  "$repo_root/scripts/verify-ci-fingerprints.sh" >"$fixture_root/sha.out" 2>&1; then
  printf '%s\n' 'CI fingerprint fixture unexpectedly accepted a mismatched SHA' >&2
  cat "$fixture_root/sha.out" >&2
  exit 1
fi

printf '%s\n' 'CI fingerprint negative fixtures: OK'
