#!/usr/bin/env bash
# Read-only validation of the market-policy v1 release and the mobile instance.
#
# The rules worth enforcing here are the ones that let a posture move without a
# decision: a market inheriting a neighbour's position, a modality borrowing the
# other's consent copy, an approval with no expiry. Each is checked structurally
# rather than left to review, because each is invisible in a diff that looks
# like configuration.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
contract_root="${SUPERNUTRITION_MARKET_POLICY_CONTRACT:-${repo_root}/contracts/market-policy/v1}"
instance_path="${SUPERNUTRITION_MARKET_POLICY_INSTANCE:-${repo_root}/mobile/config/quality/market-policy.toml}"

python3 - "$contract_root" "$instance_path" <<'PY'
from pathlib import Path
import sys
import tomllib

root = Path(sys.argv[1]).resolve()
instance_path = Path(sys.argv[2]).resolve()

schema_path = root / "schema.toml"
release_path = root / "RELEASE.md"
compat_path = root / "compatibility.toml"
fixture_paths = sorted((root / "fixtures").glob("*.toml"))
for path in (schema_path, release_path, compat_path, *fixture_paths, instance_path):
    if not path.is_file():
        raise SystemExit(f"missing market-policy file: {path}")

schema = tomllib.loads(schema_path.read_text())
if schema.get("contract_id") != "market-policy" or schema.get("release") != "v1":
    raise SystemExit("market-policy schema must identify the v1 release")
if schema.get("language_neutral") is not True or schema.get("executable") is not False:
    raise SystemExit("market-policy contract must remain language-neutral data only")

market = schema.get("market", {})
required = market.get("required_fields", [])
if not required:
    raise SystemExit("v1 must declare the fields a market has to supply")
for field in ("legal_review_approver", "legal_review_expires_on"):
    if field not in required:
        raise SystemExit(f"a market must name {field}; an approval nobody owns or that never expires is not a review")
if market.get("absent_market") != "unresolved":
    raise SystemExit("an absent market must be unresolved, not permitted by omission")
if market.get("expired_review") != "unresolved":
    raise SystemExit("an expired review must be unresolved; a lapsed approval is not a current one")
if market.get("unlisted_field") != "reject":
    raise SystemExit("an allowlist that admits unlisted fields is not an allowlist")

postures = market.get("residency_posture", [])
if "unresolved" not in postures:
    raise SystemExit("unresolved must be a declarable posture, since it is the default")
if sorted(market.get("remote_permission", [])) != ["forbidden", "permitted"]:
    raise SystemExit("remote permission is a two-value decision per modality")

consent = schema.get("consent", {})
if consent.get("per_modality_copy_version") != "required":
    raise SystemExit("each modality must name the reviewed copy that authorises it")
if consent.get("cross_modality_reuse") != "reject":
    raise SystemExit("one modality's consent copy must not authorise the other")
if consent.get("jurisdiction_and_region_shown_before_decision") is not True:
    raise SystemExit("the user must see where their content goes before deciding, not after")

independence = schema.get("independence", {})
for key, message in (
    ("catalog_country_changes_posture", "a catalog change must not move a processing position"),
    ("posture_changes_accepted_input_languages", "a posture must not change which languages resolve"),
    ("posture_changes_local_availability", "a posture must not disable the local path"),
):
    if independence.get(key) is not False:
        raise SystemExit(message)
if independence.get("local_paths_available_under_every_posture") is not True:
    raise SystemExit("the deterministic floor must remain available under every posture")

inference_rules = schema.get("inference_rules", {})
for key in ("neighbouring_market", "shared_language", "shared_currency_or_region_grouping",
            "provider_migration_widens_posture"):
    if inference_rules.get(key) != "reject":
        raise SystemExit(f"a posture must never be inferred from {key}")

authority = schema.get("authority", {})
if authority.get("ci_may_write_policy_state") is not False:
    raise SystemExit("CI must never write policy state")
if authority.get("resolver_accepts_unsigned_data") is not False:
    raise SystemExit("the resolver must refuse unsigned policy data")

# Fixtures teach the shape, so a fixture that contradicts the rules teaches the
# wrong one.
for path in fixture_paths:
    fixture = tomllib.loads(path.read_text())
    for entry in fixture.get("market", []):
        unlisted = set(entry) - set(required)
        if unlisted:
            raise SystemExit(f"{path.name} carries unlisted fields: {sorted(unlisted)}")
        missing = set(required) - set(entry)
        if missing:
            raise SystemExit(f"{path.name} omits required fields: {sorted(missing)}")
        if entry["residency_posture"] not in postures:
            raise SystemExit(f"{path.name} declares an undeclared posture")
        if entry["residency_posture"] == "unresolved" and (
            entry["photo_remote_permission"] != "forbidden" or entry["text_remote_permission"] != "forbidden"
        ):
            raise SystemExit(f"{path.name} is unresolved and still permits a transfer")
        if entry["text_remote_permission"] == "permitted" and entry["text_consent_copy_version"] == "none":
            raise SystemExit(f"{path.name} permits text transfer with no reviewed copy to authorise it")
        if entry["photo_remote_permission"] == "permitted" and entry["photo_consent_copy_version"] == "none":
            raise SystemExit(f"{path.name} permits photo transfer with no reviewed copy to authorise it")
        if entry["photo_consent_copy_version"] != "none" and (
            entry["photo_consent_copy_version"] == entry["text_consent_copy_version"]
        ):
            raise SystemExit(f"{path.name} reuses one consent copy for both modalities")

# The instance is the shipped position. An entry here is a legal claim.
instance = tomllib.loads(instance_path.read_text())
if instance.get("contract_release") != "market-policy/v1":
    raise SystemExit("the mobile instance must name the contract release it conforms to")
entries = instance.get("market", [])
if instance.get("market_count") != len(entries):
    raise SystemExit(f"instance market_count {instance.get('market_count')} does not match its {len(entries)} entries")
for entry in entries:
    missing = set(required) - set(entry)
    if missing:
        raise SystemExit(f"instance market {entry.get('market_id')} omits {sorted(missing)}")
    if entry.get("legal_review_approver") in (None, "", "none") and entry.get("residency_posture") != "unresolved":
        raise SystemExit(f"instance market {entry.get('market_id')} has a posture and no named approver")

print(f"market-policy contract: OK (v1 rules, {len(fixture_paths)} fixtures, instance declares {len(entries)} markets)")
PY
