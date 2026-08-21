#!/usr/bin/env bash
# Recomputes every "current" release digest recorded in the catalog.
#
# The catalog existed but nothing read it. contracts/release-catalog/v1/
# digests.toml recorded mobile-api/v1 as current at f42c6e31 while the tree
# hashed to 9733a365, and because no script parsed the file the disagreement was
# invisible from both directions: the release validators compared each release
# against a digest stored inside itself, and the catalog compared against
# nothing at all.
#
# This is the missing reader. It is deliberately separate from
# verify-release-immutability.sh: that gate answers "have the bytes changed
# since the base revision", using git history, while this one answers "does the
# catalog describe the bytes that are here", using only the tree. A release can
# fail either without failing the other.
set -euo pipefail

repo_root="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
catalog="${repo_root}/contracts/release-catalog/v1/digests.toml"

test -f "$catalog" || {
    printf 'missing release catalog: %s\n' "$catalog" >&2
    exit 1
}

REPO_ROOT="$repo_root" CATALOG="$catalog" python3 - <<'PY'
from collections import defaultdict
import os
from pathlib import Path
import subprocess
import sys
import tomllib

repo_root = Path(os.environ["REPO_ROOT"]).resolve()
catalog_path = Path(os.environ["CATALOG"])

failures = []


def fail(message):
    failures.append(message)


try:
    catalog = tomllib.loads(catalog_path.read_text(encoding="utf-8"))
except tomllib.TOMLDecodeError as error:
    print(f"release catalog is not valid TOML: {error}", file=sys.stderr)
    raise SystemExit(1)

if catalog.get("schema") != "supernutrition.release-catalog.v1":
    fail(f"unexpected catalog schema: {catalog.get('schema')!r}")

entries = catalog.get("entry")
if not isinstance(entries, list) or not entries:
    print("release catalog declares no entries", file=sys.stderr)
    raise SystemExit(1)

sys.path.insert(0, str(repo_root / "scripts" / "lib"))
from release_digest import ALGORITHMS as KNOWN_ALGORITHMS, declared_algorithm  # noqa: E402

REQUIRED = ("release_id", "path", "digest", "algorithm", "recorded_at_commit", "status")
KNOWN_STATUS = {"current", "superseded"}

current_by_release = defaultdict(list)

for index, entry in enumerate(entries):
    where = f"entry[{index}]"
    missing = [field for field in REQUIRED if field not in entry]
    if missing:
        fail(f"{where} is missing {', '.join(missing)}")
        continue

    status = entry["status"]
    if status not in KNOWN_STATUS:
        fail(f"{where} has unknown status {status!r}")
        continue

    digest = entry["digest"]
    if not (isinstance(digest, str) and len(digest) == 64 and all(c in "0123456789abcdef" for c in digest)):
        fail(f"{where} digest is not 64 lowercase hex characters: {digest!r}")
        continue

    if status == "superseded":
        # A superseded row is a historical record, not a claim about the tree.
        # It must say what replaced it, or it is an orphan nobody can interpret.
        if not entry.get("superseded_by_commit"):
            fail(f"{where} is superseded but records no superseded_by_commit")
        continue

    current_by_release[entry["release_id"]].append(entry)

    release_root = repo_root / entry["path"]
    if not release_root.is_dir():
        fail(f"{where} points at a missing release directory: {entry['path']}")
        continue

    if entry["algorithm"] not in KNOWN_ALGORITHMS:
        # Only an algorithm this tree implements can be recomputed. Anything
        # else must be marked superseded and carry `reproducible = false`.
        #
        # Read from the registry rather than hardcoded, so publishing a release
        # under a newer algorithm does not require editing this gate — and so
        # retiring one cannot silently strand entries that still claim it.
        fail(
            f"{where} claims current status under {entry['algorithm']!r}, "
            "which no tool in this tree can recompute"
        )
        continue

    if entry["algorithm"] != declared_algorithm(repo_root / entry["path"]):
        # The catalog and the release must name the same algorithm. If they
        # drift, the digest is recomputed one way and recorded another, and the
        # mismatch reads as tampering when it is bookkeeping — or hides
        # tampering behind it.
        fail(
            f"{where} records {entry['algorithm']!r} but the release declares "
            f"{declared_algorithm(repo_root / entry['path'])!r}"
        )
        continue

    computed = subprocess.run(
        [str(repo_root / "scripts" / "emit-release-digest.sh"), str(release_root)],
        capture_output=True,
        text=True,
        check=False,
    )
    if computed.returncode != 0:
        fail(f"{where} digest could not be computed: {computed.stderr.strip()}")
        continue

    actual = computed.stdout.strip()
    if actual != digest:
        fail(
            f"{where} records {entry['release_id']} as current at {digest[:12]} "
            f"but {entry['path']} hashes to {actual[:12]}; recompute with "
            "scripts/emit-release-digest.sh, append a new current entry, and mark "
            "this one superseded"
        )

# Exactly one live description per release. Two current rows for the same
# release means the catalog states two different things about the same bytes.
for release_id, rows in sorted(current_by_release.items()):
    if len(rows) > 1:
        fail(f"{release_id} has {len(rows)} entries marked current; exactly one may be")

# Every published release directory in the tree must appear. A release nobody
# recorded is a release this validator cannot check, which is how the drift
# stayed invisible in the first place.
recorded_paths = {entry["path"] for entry in entries if entry.get("status") == "current"}
for schema_file in sorted((repo_root / "contracts").glob("*/v[0-9]*/schema.toml")):
    release_path = str(schema_file.parent.relative_to(repo_root))
    if release_path in recorded_paths:
        continue
    schema = tomllib.loads(schema_file.read_text(encoding="utf-8"))
    # Inventories live under the same path shape but are not releases. The
    # discriminator is contract_id: a release names the contract it publishes,
    # while contracts/root-policy/v1 declares only a policy version. This is the
    # same distinction verify-release-immutability.sh makes when it exempts the
    # boundary inventory from byte-freezing.
    if "contract_id" not in schema:
        continue
    fail(f"{release_path} is a published release with no current catalog entry")

if failures:
    for message in failures:
        print(message, file=sys.stderr)
    raise SystemExit(1)

live = sum(len(rows) for rows in current_by_release.values())
print(f"release catalog: OK ({live} current entries recomputed from the tree)")
PY
