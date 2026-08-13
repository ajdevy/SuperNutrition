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

# One digest implementation, shared with scripts/emit-release-digest.sh.
export SUPERNUTRITION_SCRIPT_LIB="${repo_root}/scripts/lib"

python3 - "$contract_root" "$instance_path" <<'PY'
from pathlib import Path
import os
import sys
import tomllib

sys.path.insert(0, os.environ["SUPERNUTRITION_SCRIPT_LIB"])
from release_digest import compute as compute_release_digest

root = Path(sys.argv[1]).resolve()
instance_path = Path(sys.argv[2]).resolve()

schema_path = root / "schema.toml"
release_path = root / "RELEASE.md"
compat_path = root / "compatibility.toml"
fixture_paths = sorted((root / "fixtures").glob("*.toml"))
for path in (schema_path, release_path, compat_path, *fixture_paths, instance_path):
    if not path.is_file():
        raise SystemExit(f"missing market-policy file: {path}")

# An empty glob made every fixture rule below iterate over nothing and report OK.
# A release whose canonical examples have been deleted is not a release that
# passes; it is one nobody is checking.
if len(fixture_paths) < 2:
    raise SystemExit(
        "market-policy v1 must publish at least a permitted and an unresolved "
        f"fixture; found {len(fixture_paths)}"
    )

schema = tomllib.loads(schema_path.read_text(encoding="utf-8"))
if schema.get("contract_id") != "market-policy" or schema.get("release") != "v1":
    raise SystemExit("market-policy schema must identify the v1 release")
if schema.get("language_neutral") is not True or schema.get("executable") is not False:
    raise SystemExit("market-policy contract must remain language-neutral data only")

# The compatibility file was existence-checked and never opened, so its
# `digest = "pending"` was never computed and its declared consumers were never
# validated. A digest that says "pending" forever is not a digest.
compat = tomllib.loads(compat_path.read_text(encoding="utf-8"))
if compat.get("contract_id") != "market-policy" or compat.get("release") != "v1":
    raise SystemExit("market-policy compatibility file must target the v1 release")

recorded_digest = compat.get("digest")
if not isinstance(recorded_digest, str) or len(recorded_digest) != 64 or \
        any(character not in "0123456789abcdef" for character in recorded_digest):
    raise SystemExit(
        f"market-policy compatibility digest is {recorded_digest!r}; it must be "
        "64 lowercase hex characters. Produce one with scripts/emit-release-digest.sh"
    )
computed_digest = compute_release_digest(root)
if recorded_digest != computed_digest:
    raise SystemExit(
        f"market-policy compatibility digest is stale (computed {computed_digest})"
    )

pairs = compat.get("supported_pair", [])
if not pairs:
    raise SystemExit("market-policy compatibility file declares no consumers")
for index, pair in enumerate(pairs):
    for field in ("producer", "consumer", "direction", "expected"):
        if not pair.get(field):
            raise SystemExit(f"compatibility supported_pair[{index}] is missing {field}")
    # not-yet-implemented is a real and useful third state: it records a consumer
    # as a declared future migration rather than pretending it works today. That
    # is the honest form of a compatibility claim, and the opposite of a matrix
    # that marks unwritten consumers "accepted".
    if pair["expected"] not in ("accepted", "rejected", "not-yet-implemented"):
        raise SystemExit(
            f"compatibility supported_pair[{index}] expected must be accepted, "
            "rejected or not-yet-implemented"
        )

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

# One rule-set, applied to fixtures AND to the shipped instance.
#
# These rules used to run over fixtures only. The instance — which is the actual
# legal position, not an example of one — was checked for missing fields and a
# named approver and nothing else, so a shipped market could declare posture
# `unresolved` while permitting a photo transfer, or permit a transfer with no
# reviewed consent copy behind it, and pass. The examples were held to a higher
# standard than the thing that ships.
def check_market_entry(where, entry):
    unlisted = set(entry) - set(required)
    if unlisted:
        raise SystemExit(f"{where} carries unlisted fields: {sorted(unlisted)}")
    missing = set(required) - set(entry)
    if missing:
        raise SystemExit(f"{where} omits required fields: {sorted(missing)}")
    if entry["residency_posture"] not in postures:
        raise SystemExit(f"{where} declares an undeclared posture")
    if entry["residency_posture"] == "unresolved" and (
        entry["photo_remote_permission"] != "forbidden"
        or entry["text_remote_permission"] != "forbidden"
    ):
        raise SystemExit(f"{where} is unresolved and still permits a transfer")
    if entry["text_remote_permission"] == "permitted" and entry["text_consent_copy_version"] == "none":
        raise SystemExit(f"{where} permits text transfer with no reviewed copy to authorise it")
    if entry["photo_remote_permission"] == "permitted" and entry["photo_consent_copy_version"] == "none":
        raise SystemExit(f"{where} permits photo transfer with no reviewed copy to authorise it")
    if entry["photo_consent_copy_version"] != "none" and (
        entry["photo_consent_copy_version"] == entry["text_consent_copy_version"]
    ):
        raise SystemExit(f"{where} reuses one consent copy for both modalities")


# Fixtures teach the shape, so a fixture that contradicts the rules teaches the
# wrong one.
for path in fixture_paths:
    fixture = tomllib.loads(path.read_text(encoding="utf-8"))
    for entry in fixture.get("market", []):
        check_market_entry(path.name, entry)

# The instance is the shipped position. An entry here is a legal claim.
instance = tomllib.loads(instance_path.read_text(encoding="utf-8"))
if instance.get("contract_release") != "market-policy/v1":
    raise SystemExit("the mobile instance must name the contract release it conforms to")
entries = instance.get("market", [])
if instance.get("market_count") != len(entries):
    raise SystemExit(f"instance market_count {instance.get('market_count')} does not match its {len(entries)} entries")
for entry in entries:
    where = f"instance market {entry.get('market_id')}"
    check_market_entry(where, entry)
    if entry.get("legal_review_approver") in (None, "", "none") and entry.get("residency_posture") != "unresolved":
        raise SystemExit(f"{where} has a posture and no named approver")

print(f"market-policy contract: OK (v1 rules, {len(fixture_paths)} fixtures, instance declares {len(entries)} markets)")
PY
