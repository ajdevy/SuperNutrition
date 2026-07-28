#!/usr/bin/env bash
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
contract_root="${SUPERNUTRITION_INTERACTION_POLICY_CONTRACT:-${repo_root}/contracts/interaction-policy/v1}"

python3 - "$contract_root" <<'PY'
from hashlib import sha256
from pathlib import Path
import re
import sys
import tomllib

root = Path(sys.argv[1]).resolve()
schema_path = root / "schema.toml"
compatibility_path = root / "compatibility.toml"
fixture_path = root / "fixtures" / "defaults.toml"
for path in (schema_path, compatibility_path, fixture_path):
    if not path.is_file():
        raise SystemExit(f"missing interaction-policy contract file: {path}")

schema = tomllib.loads(schema_path.read_text())
compatibility = tomllib.loads(compatibility_path.read_text())
fixture = tomllib.loads(fixture_path.read_text())
default_allowlist = schema.get("default_allowlist", {})
allowlist = default_allowlist.get("fields")
if allowlist != ["defaultLocale", "measurementSystem", "catalogProposal", "standardServing"]:
    raise SystemExit("interaction-policy allowlist drifted")
if default_allowlist.get("standard_serving_shape") != ["unit", "amount"]:
    raise SystemExit("standard serving must remain one atomic unit/amount pair")
if default_allowlist.get("standard_serving_requires") != "canonical-item-confirmed":
    raise SystemExit("standard serving confirmation precondition drifted")
if compatibility.get("contract_id") != "interaction-policy" or compatibility.get("release") != "v1":
    raise SystemExit("interaction-policy compatibility metadata is invalid")

required_provenance = {"bundled-safe-default", "global-release", "environment", "catalog-confirmed"}
if set(schema.get("provenance", {}).get("values", [])) != required_provenance:
    raise SystemExit("interaction-policy provenance vocabulary drifted")
for case in fixture.get("forbidden_case", []):
    if case.get("expected") != "rejected":
        raise SystemExit(f"forbidden default is not rejected: {case.get('field')}")
for required in ("remoteConsent", "foodIdentity"):
    if not any(case.get("field") == required for case in fixture.get("forbidden_case", [])):
        raise SystemExit(f"missing forbidden default fixture: {required}")

compatibility_bytes = re.sub(
    rb'(?m)^digest\s*=\s*"[a-f0-9]{64}"\s*$',
    b'digest = "<release-digest>"',
    compatibility_path.read_bytes(),
)
digest = sha256()
for relative, content in sorted(
    (
        ("compatibility.toml", compatibility_bytes),
        ("fixtures/defaults.toml", fixture_path.read_bytes()),
        ("schema.toml", schema_path.read_bytes()),
    )
):
    digest.update(relative.encode("utf-8"))
    digest.update(b"\0")
    digest.update(content)
if compatibility.get("digest") != digest.hexdigest():
    raise SystemExit("interaction-policy compatibility digest is stale")

mobile_policy_path = root.parents[2] / "mobile/config/quality/interaction-policy.toml"
if mobile_policy_path.is_file():
    expected_binding = f'contract_digest = "{digest.hexdigest()}"'
    if expected_binding not in mobile_policy_path.read_text():
        raise SystemExit("mobile interaction-policy manifest is not bound to this release digest")

allowed_documentation = {"RELEASE.md"}
if any(
    path.is_symlink() or (path.name not in allowed_documentation and path.suffix != ".toml")
    for path in root.rglob("*")
    if path.is_file()
):
    raise SystemExit("interaction-policy release contains executable or source material")
print("interaction-policy contract: OK (allowlist, provenance, forbidden defaults, and digest)")
PY
