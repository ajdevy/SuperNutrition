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

# ---------------------------------------------------------------------------
# Fixtures for the rules added alongside the acknowledgement mechanism.
#
# An acknowledged edit is the one documented way past the byte-freeze, so every
# way of abusing one needs a fixture. Without these the mechanism is a hole with
# a comment beside it.
# ---------------------------------------------------------------------------

write_acknowledgement() {
    # dir, path, head_sha256, expires, extra-line
    local dir="$1"
    mkdir -p "$dir/contracts/release-catalog/v1"
    {
        printf 'schema = "supernutrition.release-acknowledged-edits.v1"\n'
        printf 'schema_version = 1\n\n[[edit]]\nrelease_id = "demo/v1"\n'
        printf 'path = "%s"\n' "$2"
        printf 'head_sha256 = "%s"\n' "$3"
        printf 'expires = "%s"\n' "$4"
        printf 'reason = "fixture"\n'
        test -z "${5:-}" || printf '%s\n' "$5"
    } >"$dir/contracts/release-catalog/v1/acknowledged-edits.toml"
}

sha_of() { shasum -a 256 "$1" | cut -d' ' -f1; }
sha_at_base() { git -C "$1" show "$2:$3" | shasum -a 256 | cut -d' ' -f1; }
commit_all() { git -C "$1" -c user.email=t@t -c user.name=t add -A && \
               git -C "$1" -c user.email=t@t -c user.name=t commit -qm "$2"; }

# --- Fixture 4: a file added to an ALREADY PUBLISHED release ----------------
# This rule did not exist before. Every addition anywhere under contracts/ was
# permitted, so a whole contract surface could be appended to a frozen release
# without the gate noticing — which is what happened to mobile-api/v1.
grow="$work/grow-published-release"
mkdir -p "$grow"
seed_repository "$grow"
base=$(git -C "$grow" rev-parse HEAD)

printf '{"outcome":"smuggled"}\n' >"$grow/contracts/demo/v1/fixtures/extra.json"
commit_all "$grow" 'append a file to a published release'

if run_gate "$grow" "$base"; then
    echo "negative fixture unexpectedly passed: file added to an already-published release" >&2
    exit 1
fi

# --- Fixture 5: modification present only in the worktree -------------------
# The gate compared base against HEAD, so an uncommitted edit to a published
# release was invisible to it while being perfectly visible to every validator
# that reads the tree.
dirty="$work/worktree-only"
mkdir -p "$dirty"
seed_repository "$dirty"
base=$(git -C "$dirty" rev-parse HEAD)

printf '{"outcome":"UNCOMMITTED"}\n' >"$dirty/contracts/demo/v1/fixtures/result.json"

if run_gate "$dirty" "$base"; then
    echo "negative fixture unexpectedly passed: published release modified in the worktree only" >&2
    exit 1
fi

# --- Fixture 6: acknowledgement that no longer describes the file -----------
# The reason head_sha256 is pinned. An acknowledgement authorises one exact
# change, not that file forever.
drifted="$work/acknowledgement-drift"
mkdir -p "$drifted"
seed_repository "$drifted"
base=$(git -C "$drifted" rev-parse HEAD)
target="contracts/demo/v1/fixtures/result.json"

printf '{"outcome":"first-edit"}\n' >"$drifted/$target"
write_acknowledgement "$drifted" "$target" "$(sha_of "$drifted/$target")" "2099-01-01" \
    "base_sha256 = \"$(sha_at_base "$drifted" "$base" "$target")\""
commit_all "$drifted" 'acknowledged edit'

if ! run_gate "$drifted" "$base"; then
    echo "positive control failed: a correctly pinned acknowledgement was rejected" >&2
    exit 1
fi

printf '{"outcome":"second-edit"}\n' >"$drifted/$target"
if run_gate "$drifted" "$base"; then
    echo "negative fixture unexpectedly passed: file re-edited after being acknowledged" >&2
    exit 1
fi

# --- Fixture 7: expired acknowledgement -------------------------------------
# The deadline is the point: an acknowledgement defers a decision, it does not
# replace one.
expired="$work/acknowledgement-expired"
mkdir -p "$expired"
seed_repository "$expired"
base=$(git -C "$expired" rev-parse HEAD)

printf '{"outcome":"stale-permission"}\n' >"$expired/$target"
write_acknowledgement "$expired" "$target" "$(sha_of "$expired/$target")" "2000-01-01" \
    "base_sha256 = \"$(sha_at_base "$expired" "$base" "$target")\""
commit_all "$expired" 'expired acknowledgement'

if run_gate "$expired" "$base"; then
    echo "negative fixture unexpectedly passed: expired acknowledgement still authorised an edit" >&2
    exit 1
fi

# --- Fixture 8: acknowledgement of the wrong KIND of change -----------------
# An entry written for a modification must not wave through an addition, or the
# `added` flag is decoration.
wrongkind="$work/acknowledgement-wrong-kind"
mkdir -p "$wrongkind"
seed_repository "$wrongkind"
base=$(git -C "$wrongkind" rev-parse HEAD)
newfile="contracts/demo/v1/fixtures/extra.json"

printf '{"outcome":"new-file"}\n' >"$wrongkind/$newfile"
write_acknowledgement "$wrongkind" "$newfile" "$(sha_of "$wrongkind/$newfile")" "2099-01-01" \
    'base_sha256 = "0000000000000000000000000000000000000000000000000000000000000000"'
commit_all "$wrongkind" 'addition carrying a modification acknowledgement'

if run_gate "$wrongkind" "$base"; then
    echo "negative fixture unexpectedly passed: addition authorised by a modification acknowledgement" >&2
    exit 1
fi

# --- Fixture 9: stale acknowledgement for an unchanged file -----------------
# Left behind, it would silently authorise the next edit to that file.
stale="$work/acknowledgement-stale"
mkdir -p "$stale"
seed_repository "$stale"
base=$(git -C "$stale" rev-parse HEAD)

write_acknowledgement "$stale" "$target" "$(sha_of "$stale/$target")" "2099-01-01" \
    'base_sha256 = "0000000000000000000000000000000000000000000000000000000000000000"'
commit_all "$stale" 'acknowledgement with nothing to acknowledge'

if run_gate "$stale" "$base"; then
    echo "negative fixture unexpectedly passed: stale acknowledgement left in place" >&2
    exit 1
fi

# --- Fixture 10: catalog status transitions ---------------------------------
# current -> superseded is the documented act of recording a new digest and must
# be allowed; the old line-count rule rejected it. The reverse would let a stale
# row be re-pointed at the live tree.
promote="$work/catalog-supersede"
mkdir -p "$promote"
seed_repository "$promote"
base=$(git -C "$promote" rev-parse HEAD)

printf '[[entry]]\nrelease_id = "demo/v1"\nstatus = "superseded"\ndigest = "%s"\n' \
    "0000000000000000000000000000000000000000000000000000000000000000" \
    >"$promote/contracts/release-catalog/v1/digests.toml"
cat >>"$promote/contracts/release-catalog/v1/digests.toml" <<'ENTRY'

[[entry]]
release_id = "demo/v1"
status = "current"
digest = "4444444444444444444444444444444444444444444444444444444444444444"
ENTRY
commit_all "$promote" 'supersede and append'

if ! run_gate "$promote" "$base"; then
    echo "positive control failed: marking an entry superseded while appending a new one was rejected" >&2
    exit 1
fi

demote="$work/catalog-demote"
mkdir -p "$demote"
seed_repository "$demote"
printf '[[entry]]\nrelease_id = "demo/v1"\nstatus = "superseded"\ndigest = "%s"\n' \
    "0000000000000000000000000000000000000000000000000000000000000000" \
    >"$demote/contracts/release-catalog/v1/digests.toml"
commit_all "$demote" 'supersede'
mid=$(git -C "$demote" rev-parse HEAD)
printf '[[entry]]\nrelease_id = "demo/v1"\nstatus = "current"\ndigest = "%s"\n' \
    "0000000000000000000000000000000000000000000000000000000000000000" \
    >"$demote/contracts/release-catalog/v1/digests.toml"
commit_all "$demote" 'promote back to current'

if run_gate "$demote" "$mid"; then
    echo "negative fixture unexpectedly passed: superseded entry promoted back to current" >&2
    exit 1
fi

# --- Fixture 11: the gate must not pass when it could not look --------------
#
# The diff used to end in `|| true` with its exit status swallowed by awk, so a
# git invocation that failed produced an empty change list and the gate reported
# OK having compared nothing. A check that goes green when it could not run is
# worse than no check: it is a green light with nothing behind it.
#
# Forced by putting a git that always fails ahead of the real one on PATH.
blind="$work/git-unavailable"
mkdir -p "$blind"
seed_repository "$blind"
base=$(git -C "$blind" rev-parse HEAD)

fake_bin="$work/fake-bin"
mkdir -p "$fake_bin"
cat >"$fake_bin/git" <<'FAKE'
#!/usr/bin/env bash
# Answer the gate's preflight so it gets as far as the diff, then fail there.
case "$1" in
  rev-parse) exit 0 ;;
  *) echo "fatal: simulated git failure" >&2; exit 128 ;;
esac
FAKE
chmod +x "$fake_bin/git"

if PATH="$fake_bin:$PATH" SUPERNUTRITION_IMMUTABILITY_BASE="$base" "$gate" "$blind" >/dev/null 2>&1; then
    echo "negative fixture unexpectedly passed: gate reported OK when git could not run" >&2
    exit 1
fi

# --- Fixture 12: a base that is not an ancestor -----------------------------
# A stale origin/main, or a revision from another line of development, diffs
# against a tree that was never this branch's starting point: it reports changes
# nobody made and misses the ones they did.
stale="$work/stale-base"
mkdir -p "$stale"
seed_repository "$stale"
git -C "$stale" -c user.email=t@t -c user.name=t checkout -q -b sidetrack
printf 'schema = "demo.v1"\n# unrelated line\n' >"$stale/contracts/demo/v1/schema.toml"
commit_all "$stale" 'a commit on another line of development'
sidetrack=$(git -C "$stale" rev-parse HEAD)
git -C "$stale" -c user.email=t@t -c user.name=t checkout -q main

if run_gate "$stale" "$sidetrack"; then
    echo "negative fixture unexpectedly passed: base was not an ancestor of HEAD" >&2
    exit 1
fi

# --- Fixture 13: a path git would quote -------------------------------------
# Without core.quotepath=false and -z, git renders a non-ASCII path as
# "contracts/caf\303\251/v1/..." — a quoted form matching neither the release
# glob nor any acknowledgement path, so the file could be edited freely.
quoted="$work/quoted-path"
mkdir -p "$quoted"
seed_repository "$quoted"
mkdir -p "$quoted/contracts/café/v1"
printf 'schema = "cafe.v1"\n' >"$quoted/contracts/café/v1/schema.toml"
commit_all "$quoted" 'publish a release with a non-ascii path'
base=$(git -C "$quoted" rev-parse HEAD)

printf 'schema = "cafe.v1"\n# edited after publication\n' >"$quoted/contracts/café/v1/schema.toml"
commit_all "$quoted" 'edit the non-ascii release'

if run_gate "$quoted" "$base"; then
    echo "negative fixture unexpectedly passed: published file under a quoted path was edited" >&2
    exit 1
fi

# --- Fixture 14: the gate names the base it compared against ----------------
# A stale local origin/main over-reports: the merge base lands further back than
# the branch point and the diff sweeps in files other merged pull requests
# published. The failure then names files the author never touched, which reads
# as a broken gate rather than a ref that needs fetching. Whichever way the base
# was chosen, the gate has to say so and say which revision it used.
provenance="$work/base-provenance"
mkdir -p "$provenance"
seed_repository "$provenance"
provenance_base=$(git -C "$provenance" rev-parse HEAD)

output=$(SUPERNUTRITION_IMMUTABILITY_BASE="$provenance_base" \
    "$repo_root/scripts/verify-release-immutability.sh" "$provenance" 2>&1)

if ! printf '%s' "$output" | grep -q "${provenance_base:0:12}"; then
    echo "negative fixture unexpectedly passed: gate did not name the base revision it used" >&2
    printf '%s\n' "$output" >&2
    exit 1
fi
if ! printf '%s' "$output" | grep -q 'passed explicitly'; then
    echo "negative fixture unexpectedly passed: gate did not say where the base came from" >&2
    printf '%s\n' "$output" >&2
    exit 1
fi

echo "release immutability negative fixtures: OK"
