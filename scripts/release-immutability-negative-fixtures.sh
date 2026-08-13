#!/usr/bin/env bash
# Negative fixtures for release immutability.
#
# The failure class these exist for is NOT "someone edited a release and forgot
# to update its digest" — every existing validator already catches that. It is
# "someone edited a published release AND updated every digest that describes it,
# in one commit". A validator reading only the working tree cannot detect that,
# because the tree is internally consistent. Only a cross-revision comparison can.
#
# Each fixture builds a throwaway git repository so the base revision is real.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
gate="${repo_root}/scripts/verify-release-immutability.sh"

# Without this, a missing or crashing gate makes every rejection fixture pass
# vacuously: "script not found" is indistinguishable from "correctly rejected".
if test ! -x "$gate"; then
    echo "release immutability gate is missing or not executable: $gate" >&2
    exit 1
fi

work=$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-release-immutability.XXXXXX")
trap 'rm -rf "$work"' EXIT

# A minimal repository with one published release and its catalog entry.
seed_repository() {
    local dir="$1"
    mkdir -p "$dir/contracts/demo/v1/fixtures" "$dir/contracts/release-catalog/v1"
    printf 'schema = "demo.v1"\n' >"$dir/contracts/demo/v1/schema.toml"
    printf '{"outcome":"baseline"}\n' >"$dir/contracts/demo/v1/fixtures/result.json"
    printf '[[entry]]\nrelease_id = "demo/v1"\nstatus = "current"\ndigest = "%s"\n' \
        "0000000000000000000000000000000000000000000000000000000000000000" \
        >"$dir/contracts/release-catalog/v1/digests.toml"
    git -C "$dir" -c init.defaultBranch=main init -q
    git -C "$dir" -c user.email=t@t -c user.name=t add -A
    git -C "$dir" -c user.email=t@t -c user.name=t commit -qm baseline
}

run_gate() {
    local dir="$1" base="$2"
    SUPERNUTRITION_IMMUTABILITY_BASE="$base" "$gate" "$dir" >/dev/null 2>&1
}

# --- Fixture 1: the co-edit attack ------------------------------------------
# Mutate a published release AND its catalog entry in one commit. Every digest
# in the tree agrees with every byte in the tree. The gate must still reject it.
attack="$work/co-edit"
mkdir -p "$attack"
seed_repository "$attack"
base=$(git -C "$attack" rev-parse HEAD)

printf '{"outcome":"ATTACKER_CONTROLLED"}\n' >"$attack/contracts/demo/v1/fixtures/result.json"
printf '[[entry]]\nrelease_id = "demo/v1"\nstatus = "current"\ndigest = "%s"\n' \
    "1111111111111111111111111111111111111111111111111111111111111111" \
    >"$attack/contracts/release-catalog/v1/digests.toml"
git -C "$attack" -c user.email=t@t -c user.name=t add -A
git -C "$attack" -c user.email=t@t -c user.name=t commit -qm 'co-edit release and catalog'

if run_gate "$attack" "$base"; then
    echo "negative fixture unexpectedly passed: published release mutated with matching catalog update" >&2
    exit 1
fi

# --- Fixture 2: catalog entry rewritten in place ----------------------------
# The release is untouched; only the recorded digest is rewritten. Append-only
# means an existing entry may never change, even when nothing else does.
rewrite="$work/catalog-rewrite"
mkdir -p "$rewrite"
seed_repository "$rewrite"
base=$(git -C "$rewrite" rev-parse HEAD)

printf '[[entry]]\nrelease_id = "demo/v1"\nstatus = "current"\ndigest = "%s"\n' \
    "2222222222222222222222222222222222222222222222222222222222222222" \
    >"$rewrite/contracts/release-catalog/v1/digests.toml"
git -C "$rewrite" -c user.email=t@t -c user.name=t add -A
git -C "$rewrite" -c user.email=t@t -c user.name=t commit -qm 'rewrite catalog entry'

if run_gate "$rewrite" "$base"; then
    echo "negative fixture unexpectedly passed: existing catalog entry rewritten in place" >&2
    exit 1
fi

# --- Fixture 3: published release file deleted ------------------------------
deletion="$work/deletion"
mkdir -p "$deletion"
seed_repository "$deletion"
base=$(git -C "$deletion" rev-parse HEAD)

rm "$deletion/contracts/demo/v1/fixtures/result.json"
git -C "$deletion" -c user.email=t@t -c user.name=t add -A
git -C "$deletion" -c user.email=t@t -c user.name=t commit -qm 'delete published fixture'

if run_gate "$deletion" "$base"; then
    echo "negative fixture unexpectedly passed: published release file deleted" >&2
    exit 1
fi

# --- Positive control: additions are permitted ------------------------------
# The gate must not block legitimate work: a NEW release and an APPENDED catalog
# entry are exactly what publishing looks like. A gate that rejects these is a
# gate nobody can ship against.
addition="$work/addition"
mkdir -p "$addition"
seed_repository "$addition"
base=$(git -C "$addition" rev-parse HEAD)

mkdir -p "$addition/contracts/demo/v2"
printf 'schema = "demo.v2"\n' >"$addition/contracts/demo/v2/schema.toml"
cat >>"$addition/contracts/release-catalog/v1/digests.toml" <<'ENTRY'

[[entry]]
release_id = "demo/v2"
status = "current"
digest = "3333333333333333333333333333333333333333333333333333333333333333"
ENTRY
git -C "$addition" -c user.email=t@t -c user.name=t add -A
git -C "$addition" -c user.email=t@t -c user.name=t commit -qm 'publish demo/v2'

if ! run_gate "$addition" "$base"; then
    echo "positive control failed: publishing a new release with an appended entry was rejected" >&2
    exit 1
fi

echo "release immutability negative fixtures: OK"
