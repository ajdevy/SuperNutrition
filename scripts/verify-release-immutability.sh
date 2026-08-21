#!/usr/bin/env bash
# Cross-revision immutability gate for published contract releases.
#
# Every other contract validator reads only the working tree, comparing a
# computed digest against a digest recorded in that same tree. That detects a
# release edited without updating its digest, and nothing else. It cannot detect
# a release edited together with every digest describing it, because the tree is
# then internally consistent — which is precisely what commit 83547213 did to
# four releases on 2026-07-31, green.
#
# The missing property is not "the digest matches the bytes" but "the bytes have
# not changed since they were published". That is only answerable against a base
# revision, so this gate reads git history rather than the tree. The base is
# derived from outside the change under review and is never read from a file in
# it, since a file in the tree is exactly what an in-tree change controls.
#
# Publishing a NEW release is the normal workflow and is always permitted.
# Adding a file to an ALREADY PUBLISHED release is not: it changes what that
# release is, and the release digest moves with it.
set -euo pipefail

target_root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

cd "$target_root"

if ! git rev-parse --git-dir >/dev/null 2>&1; then
    printf 'release immutability requires a git repository: %s\n' "$target_root" >&2
    exit 1
fi

# SUPERNUTRITION_IMMUTABILITY_BASE exists for fixtures and for CI, which knows
# its own merge base. It is an explicit argument, never a value the reviewed
# tree supplies.
base="${SUPERNUTRITION_IMMUTABILITY_BASE:-}"
base_origin="passed explicitly"
if test -z "$base"; then
    # Derived from whatever origin/main points at LOCALLY. A ref that has not
    # been fetched recently is behind the real branch, so the merge base lands
    # further back and the diff picks up work that other pull requests already
    # merged. That direction is safe — it over-reports, never under-reports —
    # but it produces failures naming files this branch never touched, which
    # reads as a broken gate rather than a stale ref.
    #
    # So the derivation is stated. CI passes the pull request base SHA and never
    # takes this path.
    base=$(git merge-base origin/main HEAD 2>/dev/null || true)
    base_origin="derived from local origin/main ($(git rev-parse --short origin/main 2>/dev/null || echo 'unknown')); run 'git fetch origin main' if this reports files you did not touch"
fi
if test -z "$base"; then
    printf 'cannot determine an immutability base revision; pass SUPERNUTRITION_IMMUTABILITY_BASE\n' >&2
    exit 1
fi
if ! git rev-parse --verify --quiet "${base}^{commit}" >/dev/null; then
    printf 'immutability base is not a commit: %s\n' "$base" >&2
    exit 1
fi

# A shallow clone silently narrows what can be compared. actions/checkout fetches
# depth 1 by default, so the base commit may exist as a graft boundary with no
# history behind it — git diff then reports far less than it should and the gate
# reports OK having compared almost nothing. Refuse rather than under-report.
if test "$(git rev-parse --is-shallow-repository 2>/dev/null)" = "true"; then
    printf 'refusing to run against a shallow clone: the comparison would be silently narrowed. Fetch full history (actions/checkout fetch-depth: 0)\n' >&2
    exit 1
fi

# TL-H3 — never certify a comparison of HEAD against itself.
#
# On a push event `github.event.pull_request.base.sha` is empty, so the fallback
# below derived the base from `merge-base origin/main HEAD` — and on a push to
# main that is HEAD. The diff was then empty by construction and the gate
# printed its affirmative OK having compared nothing. origin/main shows most
# recent commits arriving as direct pushes, one of them editing contracts/
# directly, so this was the live path for exactly the incident the gate exists
# to catch.
#
# The workflow now passes `github.event.before` on push events. This is the
# backstop for when it does not:
#
#   - base passed EXPLICITLY and equal to HEAD is a misconfigured caller. In CI a
#     real base always exists, so this is refused rather than excused — a
#     force-push whose `before` is unreachable must not read as compliance.
#   - base DERIVED and equal to HEAD means a branch with no commits of its own.
#     There is genuinely nothing to compare, so it exits clean — but it says
#     "not applicable", never "OK". A reader must not be able to mistake an
#     absent comparison for a passed one.
if test "$(git rev-parse "$base")" = "$(git rev-parse HEAD)"; then
    if test "$base_origin" = "passed explicitly"; then
        printf 'refusing to certify: the immutability base equals HEAD, so the comparison is empty by construction. On a push event pass github.event.before; on a pull request pass the base SHA.\n' >&2
        exit 1
    fi
    printf 'release immutability: NOT APPLICABLE (base %s equals HEAD; this revision has no commits to compare, so nothing was verified)\n' "$(git rev-parse --short HEAD)"
    exit 0
fi

# The base has to be an ANCESTOR of what is being reviewed. A stale origin/main —
# or a hand-passed revision from another line of development — produces a diff
# against a tree that was never this branch's starting point, which reports
# changes nobody made and misses the ones they did.
if ! git merge-base --is-ancestor "$base" HEAD 2>/dev/null; then
    printf 'immutability base %s is not an ancestor of HEAD; fetch the base branch or pass the PR base SHA\n' "${base:0:12}" >&2
    exit 1
fi

IMMUTABILITY_BASE="$base" IMMUTABILITY_BASE_ORIGIN="$base_origin" python3 - <<'PY'
from datetime import date
from hashlib import sha256
import os
from pathlib import Path
import re
import subprocess
import sys
import tomllib

base = os.environ["IMMUTABILITY_BASE"]
repo_root = Path.cwd()

CATALOG = "contracts/release-catalog/v1/digests.toml"
ACKNOWLEDGED = "contracts/release-catalog/v1/acknowledged-edits.toml"
# The root boundary inventory sits under a versioned path and so matches the
# release shape, but it is not a contract release: its schema declares no
# contract_id, the release catalog carries no entry for it, and no other product
# consumes it. It is the list of executables in this repository, which grows
# whenever a script is added — freezing it would mean no new validator could
# ever be registered without a policy version bump. The failure class these
# rules exist for is a published contract edited together with every digest
# describing it; an inventory of local scripts is not that, and
# scripts/verify-root-boundaries.sh is what holds it honest.
INVENTORY = "contracts/root-policy/v1/boundaries.toml"
EXEMPT = {CATALOG, ACKNOWLEDGED, INVENTORY}

RELEASE_FILE = re.compile(r"^contracts/[^/]+/v[0-9][^/]*/")

failures = []


def fail(message):
    failures.append(message)


def git(*args):
    """Runs git and fails the gate on error.

    The previous form ended in `|| true` and piped through awk, so a git
    invocation that errored produced an empty diff and the gate reported OK. A
    check that passes when it could not run is worse than no check: it is a
    green light with nothing behind it.
    """
    completed = subprocess.run(
        ["git", *args], capture_output=True, text=True, check=False
    )
    if completed.returncode != 0:
        print(
            f"git {' '.join(args)} failed ({completed.returncode}): "
            f"{completed.stderr.strip()}",
            file=sys.stderr,
        )
        raise SystemExit(1)
    return completed.stdout


# --- what changed -----------------------------------------------------------
#
# Diffing base against the WORKING TREE rather than against HEAD. Comparing to
# HEAD ignored uncommitted edits entirely, so a published release could be
# modified in the worktree and this gate would report OK on the very tree the
# other validators were reading.
# -z, so paths arrive NUL-separated and verbatim.
#
# Without it git quotes any path containing a non-ASCII or special character —
# "contracts/caf\303\251/v1/schema.toml" — and the quoted form matched neither
# the release glob nor any acknowledgement path, so such a file could be edited
# freely. Renames are already disabled, which keeps the record shape at
# status-then-path.
changes = []
raw = git("-c", "core.quotepath=false", "diff", "--name-status", "--no-renames",
          "-z", base, "--", "contracts/")
fields = [field for field in raw.split("\0") if field]
for index in range(0, len(fields) - 1, 2):
    changes.append((fields[index], fields[index + 1]))

# Untracked files are additions the diff above cannot see.
for path in git(
    "-c", "core.quotepath=false", "ls-files", "-z", "--others", "--exclude-standard",
    "--", "contracts/",
).split("\0"):
    if path.strip():
        changes.append(("A", path.strip()))


def released_at_base(release_prefix):
    """True when the release directory already held files at the base revision."""
    listing = git("ls-tree", "-r", "--name-only", base, "--", release_prefix)
    return any(line.strip() for line in listing.splitlines())


# --- acknowledged edits ------------------------------------------------------
acknowledgements = {}
acknowledged_path = repo_root / ACKNOWLEDGED
if acknowledged_path.is_file():
    document = tomllib.loads(acknowledged_path.read_text(encoding="utf-8"))
    if document.get("schema") != "supernutrition.release-acknowledged-edits.v1":
        fail(f"{ACKNOWLEDGED} has unexpected schema {document.get('schema')!r}")
    for index, edit in enumerate(document.get("edit", [])):
        required = ["path", "head_sha256", "expires", "reason"]
        # A modification is pinned to the bytes it started from as well as the
        # bytes it produced. An addition has no "before", so it declares itself
        # one explicitly rather than being inferred from a missing field — an
        # entry must not be able to authorise a different kind of change than
        # the one it was written for.
        if not edit.get("added"):
            required.append("base_sha256")
        missing = [field for field in required if not edit.get(field)]
        if missing:
            fail(f"{ACKNOWLEDGED} edit[{index}] is missing {', '.join(missing)}")
            continue
        acknowledgements[edit["path"]] = edit


def sha256_of_bytes(data):
    return sha256(data).hexdigest()


def acknowledged(path, kind):
    """A change is authorised only by an entry pinned to exact bytes.

    Matching on path alone would be a baseline: it would keep authorising the
    file however it changed afterwards. The resulting bytes must match, so an
    entry covers one specific change, for one specific release, until it expires.
    """
    edit = acknowledgements.get(path)
    if edit is None:
        return False, "no acknowledgement recorded"

    try:
        expires = date.fromisoformat(str(edit["expires"]))
    except ValueError:
        return False, f"acknowledgement expires {edit['expires']!r} is not a date"
    if expires < date.today():
        return False, f"acknowledgement expired on {expires.isoformat()}"

    is_addition = bool(edit.get("added"))
    if is_addition != (kind == "addition"):
        return (
            False,
            "acknowledgement records an addition but the file was modified"
            if is_addition
            else "acknowledgement records a modification but the file is new",
        )

    base_blob = subprocess.run(
        ["git", "show", f"{base}:{path}"], capture_output=True, check=False
    )
    if is_addition:
        if base_blob.returncode == 0:
            return False, "acknowledged as an addition but the file exists at the base"
    else:
        if base_blob.returncode != 0:
            return False, "acknowledged file does not exist at the base revision"
        if sha256_of_bytes(base_blob.stdout) != edit["base_sha256"]:
            return False, "acknowledgement base_sha256 does not match the base revision"

    head_file = repo_root / path
    if not head_file.is_file():
        return False, "acknowledged file is absent from the tree"
    if sha256_of_bytes(head_file.read_bytes()) != edit["head_sha256"]:
        return (
            False,
            "acknowledgement head_sha256 does not match the tree; the file changed "
            "again after it was acknowledged",
        )
    return True, ""


# --- 1. published release bytes are frozen ----------------------------------
VERBS = {"M": "modified", "D": "deleted", "T": "retyped", "R": "renamed", "C": "copied"}
used_acknowledgements = set()

for state, path in changes:
    if path in EXEMPT:
        continue
    if not RELEASE_FILE.match(path):
        continue

    code = state[0]
    if code == "A":
        # Additions are how a new release is published, and only that. Adding a
        # file to a release that already existed at the base changes what that
        # release is — the digest moves — which is the thing this gate exists to
        # stop. The previous rule permitted every addition unconditionally.
        release_prefix = "/".join(path.split("/")[:3]) + "/"
        if released_at_base(release_prefix):
            ok, why = acknowledged(path, "addition")
            if ok:
                used_acknowledgements.add(path)
                continue
            fail(
                f"{path} was added to {release_prefix}, which was already published "
                f"at {base[:12]}; publish a new version instead ({why})"
            )
        continue

    if code in VERBS:
        ok, why = acknowledged(path, "edit")
        if ok:
            used_acknowledgements.add(path)
            continue
        fail(
            f"published release file {path} was {VERBS[code]} since {base[:12]}; "
            f"releases are immutable, publish a new version instead ({why})"
        )

# An acknowledgement whose file is no longer modified has done its job and must
# not linger: left in place it would silently authorise a future re-edit.
for path in sorted(set(acknowledgements) - used_acknowledgements):
    fail(
        f"{ACKNOWLEDGED} still records {path}, which is unchanged since {base[:12]}; "
        "remove the stale entry"
    )

# --- 2. the catalog is append-only -------------------------------------------
#
# Compared structurally rather than by counting removed lines. The line count
# rejected the catalog's own documented workflow — marking a superseded entry's
# status when a new current one is appended is a line change — while accepting
# any rewrite that happened to preserve the line count.
def parse_catalog(text, label):
    try:
        return tomllib.loads(text)
    except tomllib.TOMLDecodeError as error:
        fail(f"{label} is not valid TOML: {error}")
        return None


base_catalog_blob = subprocess.run(
    ["git", "show", f"{base}:{CATALOG}"], capture_output=True, text=True, check=False
)
if base_catalog_blob.returncode == 0:
    before = parse_catalog(base_catalog_blob.stdout, f"{CATALOG} at {base[:12]}")
    head_catalog = repo_root / CATALOG
    if not head_catalog.is_file():
        fail(f"{CATALOG} was deleted; the catalog is append-only")
        after = None
    else:
        after = parse_catalog(head_catalog.read_text(encoding="utf-8"), CATALOG)

    if before is not None and after is not None:
        def identity(entry):
            return (
                entry.get("release_id"),
                entry.get("path"),
                entry.get("digest"),
                entry.get("algorithm"),
                entry.get("recorded_at_commit"),
            )

        after_by_identity = {identity(entry): entry for entry in after.get("entry", [])}
        for entry in before.get("entry", []):
            key = identity(entry)
            surviving = after_by_identity.get(key)
            if surviving is None:
                fail(
                    f"catalog entry {entry.get('release_id')} @ "
                    f"{str(entry.get('digest'))[:12]} was removed or rewritten since "
                    f"{base[:12]}; the catalog is append-only"
                )
                continue
            was, now = entry.get("status"), surviving.get("status")
            if was == now:
                continue
            # The one permitted transition, and only in one direction: a record
            # of what a release used to be may be marked superseded, never
            # promoted back to describing the current tree.
            if was == "current" and now == "superseded":
                continue
            fail(
                f"catalog entry {entry.get('release_id')} @ "
                f"{str(entry.get('digest'))[:12]} changed status {was!r} -> {now!r}; "
                "only current -> superseded is permitted"
            )

if failures:
    for message in failures:
        print(message, file=sys.stderr)
    raise SystemExit(1)

print(
    "release immutability: OK (no unacknowledged release or catalog change since "
    f"{base[:12]}, {os.environ['IMMUTABILITY_BASE_ORIGIN']})"
)
PY
