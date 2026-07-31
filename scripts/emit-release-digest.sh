#!/usr/bin/env bash
# Prints the computed digest of a contract release, and nothing else.
#
# This exists because no tool in the tree emitted one. Every validator inlined
# its own sha256 loop and reported only "stale", so an operator told their digest
# was wrong had no supported way to learn the right value, and a new release's
# catalog entry could not be produced by any documented workflow. Both gaps
# invite hand-copied digests, which is how a wrong one becomes permanent.
#
# The hashed-file set differs per contract and is declared here rather than
# rediscovered, so the catalog can record which set produced a given digest.
set -euo pipefail

usage() {
    cat >&2 <<'USAGE'
usage: emit-release-digest.sh <contract-release-root>

Prints the release digest as 64 lowercase hex characters.

  scripts/emit-release-digest.sh contracts/inference/v1
USAGE
    exit 2
}

test "$#" -eq 1 || usage
release_root="$1"
test -d "$release_root" || {
    printf 'not a release directory: %s\n' "$release_root" >&2
    exit 1
}

python3 - "$release_root" <<'PY'
from hashlib import sha256
from pathlib import Path
import re
import sys

root = Path(sys.argv[1]).resolve()

# Which file carries the self-referential digest field, per contract shape.
# It is normalized out before hashing so the digest does not depend on itself.
matrix_candidates = ("compatibility-matrix.toml", "compatibility.toml")
matrix_path = next((root / name for name in matrix_candidates if (root / name).is_file()), None)
if matrix_path is None:
    raise SystemExit(f"no compatibility file found in {root}")

SELF_REFERENCE = re.compile(
    rb'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|pending|<release-digest>)"\s*$'
)

release_files = []
for path in sorted(root.rglob("*")):
    if not path.is_file():
        continue
    relative = str(path.relative_to(root))
    content = path.read_bytes()
    if path == matrix_path:
        content = SELF_REFERENCE.sub(b'digest = "<release-digest>"', content)
    release_files.append((relative, content))

if not release_files:
    raise SystemExit(f"release directory is empty: {root}")

digest = sha256()
for relative, content in sorted(release_files):
    digest.update(relative.encode("utf-8"))
    digest.update(b"\0")
    digest.update(content)

print(digest.hexdigest())
PY
