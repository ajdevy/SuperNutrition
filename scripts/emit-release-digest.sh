#!/usr/bin/env bash
# Prints the computed digest of a contract release, and nothing else.
#
# This exists because no tool in the tree emitted one. Every validator inlined
# its own sha256 loop and reported only "stale", so an operator told their digest
# was wrong had no supported way to learn the right value, and a new release's
# catalog entry could not be produced by any documented workflow. Both gaps
# invite hand-copied digests, which is how a wrong one becomes permanent.
#
# The algorithm itself lives in scripts/lib/release_digest.py so this emitter and
# every verifier compute the same number. They did not: this script hashed every
# file in the release while scripts/verify-mobile-api-contract.sh hashed only
# *.toml, so the workflow RELEASE.md documents — run the emitter, paste the
# value — produced a digest that verifier rejected.
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

script_lib="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/lib"

python3 - "$release_root" "$script_lib" <<'PY'
from pathlib import Path
import sys

sys.path.insert(0, sys.argv[2])
from release_digest import compute

print(compute(Path(sys.argv[1]).resolve()))
PY
