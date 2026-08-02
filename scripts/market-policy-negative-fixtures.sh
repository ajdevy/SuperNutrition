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

run_case() {
    local name="$1"
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

echo "market policy negative fixtures: OK"
