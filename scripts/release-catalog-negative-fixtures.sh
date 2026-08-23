#!/usr/bin/env bash
# Negative fixtures for scripts/verify-release-catalog.sh.
#
# The provenance checks were probe-verified when they were written and then
# committed with no fixture at all — there was no release-catalog fixture script
# in the tree. A check nobody exercises is a check that can be deleted or
# narrowed without anything going red.
#
# The catalog is seeded into a state the validator ACCEPTS, proved by a positive
# control, and each fixture breaks exactly one field.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
validator="$script_dir/verify-release-catalog.sh"

test -x "$validator" || {
    printf 'release catalog validator is missing or not executable: %s\n' "$validator" >&2
    exit 1
}

work="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-release-catalog.XXXXXX")"
trap 'rm -rf "$work"' EXIT
fixture_count=0

# A copy of the real tree: the validator recomputes every current release from
# the filesystem, so a synthetic catalog with no releases behind it would be
# rejected for that instead of for the field under test.
seed_repository() {
    rm -rf "$work/repo"
    python3 - "$repo_root" "$work/repo" <<'PY_COPY'
import pathlib
import shutil
import sys

source, target = (pathlib.Path(argument) for argument in sys.argv[1:3])
for relative in ("contracts", "scripts"):
    shutil.copytree(
        source / relative,
        target / relative,
        dirs_exist_ok=True,
        ignore=shutil.ignore_patterns("__pycache__", ".git"),
    )
PY_COPY
}

catalog() { printf '%s' "$work/repo/contracts/release-catalog/v1/digests.toml"; }

run_validator() {
    "$validator" "$work/repo" >"$work/out.log" 2>&1
}

expect_rejected_because() {
    local description="$1" expected="$2"
    if run_validator; then
        printf 'negative fixture unexpectedly accepted: %s\n' "$description" >&2
        exit 1
    fi
    if ! grep -qi -- "$expected" "$work/out.log"; then
        printf 'fixture "%s" was rejected, but not for its own reason.\n' "$description" >&2
        printf '  expected the message to mention: %s\n' "$expected" >&2
        printf '  actual: %s\n' "$(tail -1 "$work/out.log")" >&2
        exit 1
    fi
    fixture_count=$((fixture_count + 1))
}

replace_first() {
    python3 - "$(catalog)" "$1" "$2" <<'PY_EDIT'
import pathlib
import sys

path, old, new = pathlib.Path(sys.argv[1]), sys.argv[2], sys.argv[3]
text = path.read_text(encoding="utf-8")
if old not in text:
    sys.exit(f"fixture anchor not present in the catalog: {old}")
path.write_text(text.replace(old, new, 1), encoding="utf-8")
PY_EDIT
}

# --- positive control, first -------------------------------------------------
seed_repository
if ! run_validator; then
    printf 'positive control failed: an unmutated catalog was rejected\n' >&2
    tail -3 "$work/out.log" >&2
    exit 1
fi

# --- a branch name is not a revision -----------------------------------------
# Three entries recorded `pr/slice-ab-backend` here. A branch stops resolving the
# moment it is squashed or deleted, taking the catalog's only human-readable
# provenance with it.
seed_repository
replace_first 'recorded_at_commit = "daa51abceb20b98dee9e9d692b7cadb5e2ad08e9"' \
    'recorded_at_commit = "pr/some-branch"'
expect_rejected_because "recorded_at_commit naming a branch" "not a commit revision"

seed_repository
replace_first 'superseded_by_commit = "pending"' 'superseded_by_commit = "pr/some-branch"'
expect_rejected_because "superseded_by_commit naming a branch" "not a commit revision"

# --- not hex is not a revision either ----------------------------------------
seed_repository
replace_first 'recorded_at_commit = "daa51abceb20b98dee9e9d692b7cadb5e2ad08e9"' \
    'recorded_at_commit = "zzzzzzz"'
expect_rejected_because "a recorded_at_commit that is not hex" "not a commit revision"

# --- `pending` is permitted for the superseding commit ONLY ------------------
# A superseding commit genuinely does not exist until the merge, so the declared
# unknown is honest there. It is not honest about when a digest was recorded.
seed_repository
replace_first 'recorded_at_commit = "daa51abceb20b98dee9e9d692b7cadb5e2ad08e9"' \
    'recorded_at_commit = "pending"'
expect_rejected_because "recorded_at_commit borrowing the pending escape" "not a commit revision"

# --- the catalog and the release must name the same algorithm ---------------
# Targeted at a CURRENT entry: a superseded row is a historical record and the
# validator deliberately stops before recomputing it, so mutating one would have
# proved nothing.
seed_repository
python3 - "$(catalog)" <<'PY_ALGORITHM'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
blocks = path.read_text(encoding="utf-8").split("[[entry]]")
for index, block in enumerate(blocks):
    if 'status = "current"' in block and 'algorithm = "release-sha256-v2"' in block:
        blocks[index] = block.replace(
            'algorithm = "release-sha256-v2"', 'algorithm = "release-sha256-v3"', 1
        )
        path.write_text("[[entry]]".join(blocks), encoding="utf-8")
        break
else:
    sys.exit("no current entry carrying release-sha256-v2 to mutate")
PY_ALGORITHM
expect_rejected_because "an algorithm the release does not declare" "declares"

printf 'release-catalog negative fixtures: OK (%d rejections, each for its own reason)\n' \
    "$fixture_count"
