#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-ci-fingerprint.XXXXXX")"
trap 'rm -rf "$fixture_root"' EXIT

jobs=(mobile-unit-coverage)
for job in "${jobs[@]}"; do
  "$repo_root/scripts/emit-ci-fingerprint.sh" "$job" "$fixture_root/$job.fingerprint"
done

SUPERNUTRITION_CI_FINGERPRINT_ROOT="$fixture_root" \
SUPERNUTRITION_EXPECTED_SHA="$(git -C "$repo_root" rev-parse HEAD)" \
  "$repo_root/scripts/verify-ci-fingerprints.sh" >/dev/null

expected_sha="$(git -C "$repo_root" rev-parse HEAD)"
python3 - "$fixture_root/mobile-unit-coverage.fingerprint" "$expected_sha" <<'PY'
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

# --- a catalog with no current releases must not produce a fingerprint --------
# The guard printed its refusal from inside `$( )` in argument position, where
# the exit status is discarded: the message appeared, the loop ran over zero
# releases, the file was written and the script exited 0. A fingerprint covering
# nothing is exactly as green as one covering everything.
empty_root="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-ci-fingerprint-empty.XXXXXX")"
trap 'rm -rf "$empty_root"' EXIT
python3 - "$repo_root" "$empty_root" <<'PY_COPY'
import pathlib
import shutil
import sys

source, target = (pathlib.Path(argument) for argument in sys.argv[1:3])
for relative in ("contracts", "scripts", "mobile", "web", "backend", ".github"):
    if (source / relative).exists():
        shutil.copytree(
            source / relative,
            target / relative,
            dirs_exist_ok=True,
            ignore=shutil.ignore_patterns(
                "build", "node_modules", ".gradle", ".git", "dist", "coverage", "*.jar"
            ),
        )
PY_COPY

# The emitter reads a commit SHA, so the copy has to be a checkout. Committed
# rather than merely initialised: `git rev-parse HEAD` needs something to point
# at.
git -C "$empty_root" -c init.defaultBranch=main init -q
git -C "$empty_root" -c user.email=t@t -c user.name=t add -A >/dev/null 2>&1
git -C "$empty_root" -c user.email=t@t -c user.name=t commit -qm 'fixture baseline' >/dev/null 2>&1

# POSITIVE CONTROL FIRST. Without it, the refusal below could be about the copy
# being incomplete rather than about the catalog — a fixture passing for a
# reason it was not written to test, which is how the first draft of this one
# passed against the unfixed emitter.
control="$empty_root/control.txt"
if ! SUPERNUTRITION_CI_FINGERPRINT_ROOT="$empty_root" \
  "$repo_root/scripts/emit-ci-fingerprint.sh" mobile-unit-coverage "$control" \
  >"$empty_root/control.log" 2>&1; then
  printf '%s\n' 'positive control failed: the emitter could not run against an unmutated copy' >&2
  tail -3 "$empty_root/control.log" >&2
  exit 1
fi

catalog="$empty_root/contracts/release-catalog/v1/digests.toml"
python3 - "$catalog" <<'PY_EMPTY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
path.write_text(
    path.read_text(encoding="utf-8").replace('status = "current"', 'status = "superseded"'),
    encoding="utf-8",
)
PY_EMPTY

emitted="$empty_root/fingerprint.txt"
output="$(SUPERNUTRITION_CI_FINGERPRINT_ROOT="$empty_root" \
  "$repo_root/scripts/emit-ci-fingerprint.sh" mobile-unit-coverage "$emitted" 2>&1 || true)"
if SUPERNUTRITION_CI_FINGERPRINT_ROOT="$empty_root" \
  "$repo_root/scripts/emit-ci-fingerprint.sh" mobile-unit-coverage "$emitted" >/dev/null 2>&1; then
  printf '%s\n' 'CI fingerprint fixture unexpectedly emitted from an empty catalog' >&2
  exit 1
fi
case "$output" in
  *"refusing to fingerprint nothing"*) : ;;
  *)
    printf '%s\n' 'the emitter refused, but not for the empty catalog' >&2
    printf '  actual: %s\n' "$output" >&2
    exit 1
    ;;
esac
if test -f "$emitted"; then
  printf '%s\n' 'CI fingerprint fixture wrote a file despite refusing to fingerprint' >&2
  exit 1
fi

printf '%s\n' 'CI fingerprint negative fixtures: OK'
