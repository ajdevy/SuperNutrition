#!/usr/bin/env bash
# Negative fixtures for the market-policy contract.
#
# Every case here is a way a processing position could widen without anyone
# deciding to widen it. A verifier that only passes on the happy path proves
# nothing about the rules it claims to enforce, and these rules are the kind
# whose violation looks like ordinary configuration in a diff.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
verifier="${repo_root}/scripts/verify-market-policy-contract.sh"

if test ! -x "$verifier"; then
    echo "market-policy verifier is missing or not executable: $verifier" >&2
    exit 1
fi

work=$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-market-policy.XXXXXX")
trap 'rm -rf "$work"' EXIT

seed() {
    local name="$1"
    mkdir -p "$work/$name"
    cp -R "${repo_root}/contracts/market-policy/v1" "$work/$name/contract"
    cp "${repo_root}/mobile/config/quality/market-policy.toml" "$work/$name/instance.toml"
}

# Recomputes the release digest into the fixture's compatibility file.
#
# Needed the moment the verifier started enforcing that digest (M14): any
# mutation to the contract also invalidates it, so without this every fixture is
# answered by "digest is stale" and none of them reach the rule they were written
# for. That is exactly the trap M18 found in the mobile-api harness.
repin_contract() {
    SUPERNUTRITION_SCRIPT_LIB="${repo_root}/scripts/lib" python3 - "$work/$1/contract" <<'REPIN'
from pathlib import Path
import os
import re
import sys

sys.path.insert(0, os.environ["SUPERNUTRITION_SCRIPT_LIB"])
from release_digest import compute

root = Path(sys.argv[1])
compat = root / "compatibility.toml"
compat.write_text(
    re.sub(
        r'^digest = "(?:[a-f0-9]{64}|pending)"$',
        f'digest = "{compute(root)}"',
        compat.read_text(),
        count=1,
        flags=re.M,
    )
)
REPIN
}

# Repins by default. A fixture that is ABOUT the digest opts out with
# SUPERNUTRITION_FIXTURE_NO_REPIN=1, so the default is the safe one and a new
# fixture cannot silently regress into testing staleness instead of its rule.
run_case() {
    local name="$1"
    test -n "${SUPERNUTRITION_FIXTURE_NO_REPIN:-}" || repin_contract "$name"
    SUPERNUTRITION_MARKET_POLICY_CONTRACT="$work/$name/contract" \
        SUPERNUTRITION_MARKET_POLICY_INSTANCE="$work/$name/instance.toml" \
        "$verifier" 2>&1
}

expect_rejected_because() {
    local label="$1" name="$2" pattern="$3" output
    if output=$(run_case "$name"); then
        echo "negative fixture unexpectedly passed: $label" >&2
        exit 1
    fi
    if ! printf '%s' "$output" | grep -Fq "$pattern"; then
        echo "negative fixture rejected for the wrong reason: $label" >&2
        echo "  expected message containing: $pattern" >&2
        echo "  got: $output" >&2
        exit 1
    fi
}

replace_once() {
    python3 - "$1" "$2" "$3" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
if sys.argv[2] not in text:
    raise SystemExit(f"fixture setup failed: {sys.argv[2]!r} not found in {path}")
path.write_text(text.replace(sys.argv[2], sys.argv[3], 1))
PY
}

# Positive control: an unmutated copy must pass, or every rejection below proves
# only that the copy is broken.
seed baseline
if ! run_case baseline >/dev/null; then
    echo "positive control failed: an unmutated copy was rejected" >&2
    exit 1
fi

# --- omission is not permission ---------------------------------------------
seed absent_permits
replace_once "$work/absent_permits/contract/schema.toml" 'absent_market = "unresolved"' 'absent_market = "permitted"'
expect_rejected_because "a market permitted by being absent" absent_permits "not permitted by omission"

seed expired_permits
replace_once "$work/expired_permits/contract/schema.toml" 'expired_review = "unresolved"' 'expired_review = "permitted"'
expect_rejected_because "a lapsed approval treated as current" expired_permits "a lapsed approval is not a current one"

# --- an approval nobody owns, or that never lapses ---------------------------
seed no_expiry
replace_once "$work/no_expiry/contract/schema.toml" '  "legal_review_expires_on",
' ''
expect_rejected_because "an approval with no expiry" no_expiry "never expires is not a review"

# --- unresolved must remain declarable --------------------------------------
seed no_unresolved
replace_once "$work/no_unresolved/contract/schema.toml" '  "unresolved",
  "in-country-only",' '  "in-country-only",'
expect_rejected_because "unresolved removed from the postures" no_unresolved "since it is the default"

# --- a posture inherited rather than decided --------------------------------
seed neighbour
replace_once "$work/neighbour/contract/schema.toml" 'neighbouring_market = "reject"' 'neighbouring_market = "inherit"'
expect_rejected_because "a posture inferred from a neighbour" neighbour "must never be inferred from neighbouring_market"

seed language
replace_once "$work/language/contract/schema.toml" 'shared_language = "reject"' 'shared_language = "inherit"'
expect_rejected_because "a posture inferred from a shared language" language "must never be inferred from shared_language"

seed provider
replace_once "$work/provider/contract/schema.toml" 'provider_migration_widens_posture = "reject"' 'provider_migration_widens_posture = "allow"'
expect_rejected_because "a provider migration widening a posture" provider "must never be inferred from provider_migration_widens_posture"

# --- consent borrowed across modalities -------------------------------------
seed cross_modality
replace_once "$work/cross_modality/contract/schema.toml" 'cross_modality_reuse = "reject"' 'cross_modality_reuse = "allow"'
expect_rejected_because "one modality's copy authorising the other" cross_modality "must not authorise the other"

seed shared_copy
replace_once "$work/shared_copy/contract/fixtures/permitted-market.toml" \
    'text_consent_copy_version = "none"' 'text_consent_copy_version = "photo-transfer-copy-v1"'
expect_rejected_because "a fixture reusing one copy for both modalities" shared_copy "reuses one consent copy"

# --- a transfer with nothing authorising it ---------------------------------
seed permit_without_copy
replace_once "$work/permit_without_copy/contract/fixtures/permitted-market.toml" \
    'text_remote_permission = "forbidden"' 'text_remote_permission = "permitted"'
expect_rejected_because "text permitted with no reviewed copy" permit_without_copy "no reviewed copy to authorise it"

# --- the local path is not a posture's to disable ---------------------------
seed local_disabled
replace_once "$work/local_disabled/contract/schema.toml" 'posture_changes_local_availability = false' 'posture_changes_local_availability = true'
expect_rejected_because "a posture disabling the local path" local_disabled "must not disable the local path"

seed catalog_moves_posture
replace_once "$work/catalog_moves_posture/contract/schema.toml" 'catalog_country_changes_posture = false' 'catalog_country_changes_posture = true'
expect_rejected_because "a catalog change moving a posture" catalog_moves_posture "must not move a processing position"

# --- policy written by a pipeline -------------------------------------------
seed ci_writes
replace_once "$work/ci_writes/contract/schema.toml" 'ci_may_write_policy_state = false' 'ci_may_write_policy_state = true'
expect_rejected_because "CI permitted to write policy state" ci_writes "CI must never write policy state"

# --- the instance itself ----------------------------------------------------
seed miscounted
replace_once "$work/miscounted/instance.toml" 'market_count = 0' 'market_count = 3'
expect_rejected_because "an instance whose count disagrees with its entries" miscounted "does not match its 0 entries"

seed posture_without_approver
python3 - "$work/posture_without_approver/instance.toml" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
path.write_text(path.read_text().replace("market_count = 0", "market_count = 1") + '''
[[market]]
market_id = "QQ"
jurisdiction = "somewhere"
residency_posture = "permitted-with-region"
permitted_processing_regions = ["region-1"]
photo_remote_permission = "permitted"
text_remote_permission = "forbidden"
photo_consent_copy_version = "photo-transfer-copy-v1"
text_consent_copy_version = "none"
accepted_input_languages = ["en"]
legal_review_approver = "none"
legal_review_expires_on = "2027-01-01"
''')
PY
expect_rejected_because "a shipped posture with no named approver" posture_without_approver "no named approver"

# --- M14: the compatibility digest was never computed -----------------------
# It said "pending" from publication, and the verifier only checked that the file
# existed. A digest nobody computes is a field, not a check.
seed pending_digest
replace_once "$work/pending_digest/contract/compatibility.toml" \
    "$(grep -o 'digest = "[a-f0-9]\{64\}"' "${repo_root}/contracts/market-policy/v1/compatibility.toml")" \
    'digest = "pending"'
SUPERNUTRITION_FIXTURE_NO_REPIN=1 \
    expect_rejected_because "a compatibility digest left as pending" pending_digest "64 lowercase hex"

# --- M14: a stale compatibility digest --------------------------------------
seed stale_digest
replace_once "$work/stale_digest/contract/compatibility.toml" \
    "$(grep -o 'digest = "[a-f0-9]\{64\}"' "${repo_root}/contracts/market-policy/v1/compatibility.toml")" \
    "digest = \"$(printf '0%.0s' $(seq 64))\""
SUPERNUTRITION_FIXTURE_NO_REPIN=1 \
    expect_rejected_because "a stale compatibility digest" stale_digest "digest is stale"

# --- M14: the fixture set emptied -------------------------------------------
# An empty glob made every fixture rule iterate over nothing and report OK, so
# deleting the canonical examples was indistinguishable from honouring them.
seed no_fixtures
rm -f "$work/no_fixtures/contract/fixtures/"*.toml
expect_rejected_because "a release with no canonical fixtures" no_fixtures "at least a permitted and an unresolved"

# --- M14: the SHIPPED instance held to the fixture rules --------------------
# These rules ran over fixtures only. The instance is the actual legal position,
# not an example of one, and it was checked for missing fields and a named
# approver and nothing else — so this entry, unresolved and still permitting a
# photo transfer, passed.
seed unresolved_but_permitting
python3 - "$work/unresolved_but_permitting/instance.toml" <<'PY2'
from pathlib import Path
import sys
path = Path(sys.argv[1])
path.write_text(path.read_text().replace("market_count = 0", "market_count = 1") + '''
[[market]]
market_id = "QQ"
jurisdiction = "somewhere"
residency_posture = "unresolved"
permitted_processing_regions = []
photo_remote_permission = "permitted"
text_remote_permission = "forbidden"
photo_consent_copy_version = "photo-transfer-copy-v1"
text_consent_copy_version = "none"
accepted_input_languages = ["en"]
legal_review_approver = "a. reviewer"
legal_review_expires_on = "2027-01-01"
''')
PY2
expect_rejected_because "a shipped market that is unresolved and still permits a transfer" \
    unresolved_but_permitting "unresolved and still permits a transfer"

# --- M14: a shipped market permitting transfer with no reviewed copy --------
seed permitted_without_copy
python3 - "$work/permitted_without_copy/instance.toml" <<'PY3'
from pathlib import Path
import sys
path = Path(sys.argv[1])
path.write_text(path.read_text().replace("market_count = 0", "market_count = 1") + '''
[[market]]
market_id = "QQ"
jurisdiction = "somewhere"
residency_posture = "permitted-with-region"
permitted_processing_regions = ["region-1"]
photo_remote_permission = "permitted"
text_remote_permission = "forbidden"
photo_consent_copy_version = "none"
text_consent_copy_version = "none"
accepted_input_languages = ["en"]
legal_review_approver = "a. reviewer"
legal_review_expires_on = "2027-01-01"
''')
PY3
expect_rejected_because "a shipped market permitting photo transfer with no reviewed copy" \
    permitted_without_copy "no reviewed copy"

echo "market policy negative fixtures: OK"
