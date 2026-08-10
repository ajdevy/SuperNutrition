"""The one implementation of release-sha256-v2.

WHY THIS FILE EXISTS
Two tools computed a release digest and they disagreed. scripts/emit-release-
digest.sh hashed every file under the release root; scripts/verify-mobile-api-
contract.sh hashed only `*.toml`. So contracts/mobile-api/v1 has two defensible
digests — 9733a365 over all files, b9829d6d over the TOML — and the workflow
RELEASE.md documents ("run the emitter, paste the value into
compatibility-matrix.toml") produced a number the verifier rejected. An operator
told their digest is stale, who then follows the only documented way to get a
fresh one and is told it is stale again, ends up hand-editing until something
passes. That is how a wrong digest becomes permanent.

WHICH ONE IS RIGHT
All files. RELEASE.md describes the digest as covering "the sorted release
files", schema.toml declares `documentation = ["RELEASE.md"]` as a member of the
release, and the release-catalog records these releases under algorithm
`release-sha256-v2`, whose defining change (commit 83547213) was precisely that
RELEASE.md became a hashed input. The TOML-only variant was the outlier.

THE ALGORITHM
Files are sorted by their path relative to the release root. For each, the
relative path is hashed, then a NUL, then the bytes. The compatibility file's
own `digest = "..."` line is replaced with a placeholder first, so a release
digest does not depend on itself.
"""

from hashlib import sha256
from pathlib import Path
import re

# The compatibility file carries the self-referential digest field. Normalised
# out before hashing, in whichever of the two shapes a release uses.
MATRIX_CANDIDATES = ("compatibility-matrix.toml", "compatibility.toml")

SELF_REFERENCE = re.compile(rb'(?m)^digest\s*=\s*"(?:[a-f0-9]{64}|pending|<release-digest>)"\s*$')

PLACEHOLDER = b'digest = "<release-digest>"'


def matrix_path(root: Path) -> Path:
    """The file inside `root` that records the release's own digest."""
    found = next((root / name for name in MATRIX_CANDIDATES if (root / name).is_file()), None)
    if found is None:
        raise FileNotFoundError(f"no compatibility file found in {root}")
    return found


def release_files(root: Path) -> list[tuple[str, bytes]]:
    """Every file in the release, with the self-reference normalised out."""
    matrix = matrix_path(root)
    members: list[tuple[str, bytes]] = []
    for path in sorted(root.rglob("*")):
        if not path.is_file():
            continue
        content = path.read_bytes()
        if path == matrix:
            content = SELF_REFERENCE.sub(PLACEHOLDER, content)
        members.append((path.relative_to(root).as_posix(), content))
    if not members:
        raise ValueError(f"release directory is empty: {root}")
    return members


def compute(root: Path) -> str:
    """release-sha256-v2 over `root`, as 64 lowercase hex characters."""
    digest = sha256()
    for relative, content in sorted(release_files(root)):
        digest.update(relative.encode("utf-8"))
        digest.update(b"\0")
        digest.update(content)
    return digest.hexdigest()


def declared_members(schema: dict) -> list[str]:
    """The release's own declaration of what belongs to it.

    Returned so a caller can check the filesystem against the declaration: a file
    present but undeclared, or declared but missing, means the digest covers a
    set nobody wrote down.
    """
    members: list[str] = []
    for key in ("schemas", "fixtures", "documentation"):
        value = schema.get("files", {}).get(key, [])
        if isinstance(value, list):
            members.extend(str(entry) for entry in value)
    return sorted(members)
