#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
contract_root="${SUPERNUTRITION_MOBILE_API_CONTRACT:-${repo_root}/contracts/mobile-api/v1}"

python3 - "$contract_root" <<'PY'
from hashlib import sha256
from pathlib import Path
import re
import sys
import tomllib

root = Path(sys.argv[1]).resolve()
schema_path = root / "schema.toml"
matrix_path = root / "compatibility-matrix.toml"
fixture_path = root / "fixtures" / "empty-analysis-request.toml"
release_path = root / "RELEASE.md"
for path in (schema_path, matrix_path, fixture_path, release_path):
    if not path.is_file():
        raise SystemExit(f"missing mobile-api contract file: {path}")

schema = tomllib.loads(schema_path.read_text())
matrix = tomllib.loads(matrix_path.read_text())
fixture = tomllib.loads(fixture_path.read_text())
if schema.get("contract_id") != "mobile-api":
    raise SystemExit("mobile-api schema must declare the top-level contract_id")
if schema.get("language_neutral") is not True or schema.get("executable") is not False:
    raise SystemExit("mobile-api contract must remain language-neutral and non-executable")
if schema.get("release") != "v1" or matrix.get("release") != "v1":
    raise SystemExit("mobile-api schema and compatibility matrix must target release v1")
if matrix.get("contract_id") != "mobile-api" or fixture.get("expected") != "rejected":
    raise SystemExit("mobile-api fixture does not bind the declared contract")
if fixture.get("reason") != "missing-normalized-photo-payload":
    raise SystemExit("empty-analysis-request fixture must reject missing normalized bytes")

pairs = {
    (item.get("producer"), item.get("consumer"), item.get("direction"), item.get("expected"))
    for item in matrix.get("supported_pair", [])
}
required_pairs = {
    ("mobile-v1", "backend-v1", "forward", "accepted"),
    ("backend-v1", "mobile-v1", "backward", "accepted"),
}
if pairs != required_pairs:
    raise SystemExit(f"compatibility matrix must contain exactly the v1 skew pairs: {sorted(pairs)}")

matrix_bytes = re.sub(
    rb'(?m)^digest\s*=\s*"[a-f0-9]{64}"\s*$',
    b'digest = "<release-digest>"',
    matrix_path.read_bytes(),
)
digest = sha256()
for relative, content in sorted(
    (
        ("compatibility-matrix.toml", matrix_bytes),
        ("fixtures/empty-analysis-request.toml", fixture_path.read_bytes()),
        ("RELEASE.md", release_path.read_bytes()),
        ("schema.toml", schema_path.read_bytes()),
    )
):
    digest.update(relative.encode("utf-8"))
    digest.update(b"\0")
    digest.update(content)
if matrix.get("digest") != digest.hexdigest():
    raise SystemExit("mobile-api compatibility digest is stale")

allowed_documentation = {"RELEASE.md"}
if any(
    path.is_symlink() or (path.name not in allowed_documentation and path.suffix != ".toml")
    for path in root.rglob("*")
    if path.is_file()
):
    raise SystemExit("mobile-api contract release contains executable or source material")
print("mobile-api contract: OK (v1 schema, immutable fixture, and bidirectional compatibility matrix)")
PY
