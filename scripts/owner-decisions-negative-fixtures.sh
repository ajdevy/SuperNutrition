#!/usr/bin/env bash
# Negative fixtures for scripts/verify-owner-decisions.sh.
#
# The record is seeded into a state the validator ACCEPTS, proved by a positive
# control, and each fixture then breaks exactly one thing. Without the control,
# every rejection below could be about the seed rather than the mutation — the
# failure mode the root-boundary fixtures were rewritten to remove.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
validator="$script_dir/verify-owner-decisions.sh"

test -x "$validator" || {
    printf 'owner decision validator is missing or not executable: %s\n' "$validator" >&2
    exit 1
}

work="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-owner-decisions.XXXXXX")"
trap 'rm -rf "$work"' EXIT
record="$work/OWNER-DECISIONS.md"
fixture_count=0

seed_valid_record() {
    cat >"$record" <<'RECORD'
# Deferred owner decisions

## D-01 — a decision (#1)

Decided on: 2026-08-15
Decided by: repository owner
Channel: PR #44 comment

**Decided: something.**
RECORD
}

run_validator() {
    SUPERNUTRITION_OWNER_DECISIONS="$record" "$validator" "$@" >/dev/null 2>&1
}

expect_rejected() {
    local description="$1"
    if run_validator; then
        printf 'negative fixture unexpectedly accepted: %s\n' "$description" >&2
        exit 1
    fi
    fixture_count=$((fixture_count + 1))
}

# --- positive control, first ------------------------------------------------
seed_valid_record
if ! run_validator; then
    printf 'positive control failed: a complete record was rejected\n' >&2
    SUPERNUTRITION_OWNER_DECISIONS="$record" "$validator" 2>&1 | tail -3 >&2
    exit 1
fi

# --- the three fields, each missing in turn ----------------------------------
# EV-1 is exactly this: a decision that exists without saying who made it.
for field in 'Decided on' 'Decided by' 'Channel'; do
    seed_valid_record
    grep -v "^${field}:" "$record" >"$record.tmp" && mv "$record.tmp" "$record"
    expect_rejected "a decision with no ${field}"
done

# --- a date that is not a date ------------------------------------------------
seed_valid_record
sed 's/^Decided on:.*/Decided on: last tuesday/' "$record" >"$record.tmp" && mv "$record.tmp" "$record"
expect_rejected "a decision whose date does not parse"

# --- a date that has not happened --------------------------------------------
# A typo or a backdated record, and both are worth catching while someone still
# remembers which.
seed_valid_record
sed 's/^Decided on:.*/Decided on: 2099-01-01/' "$record" >"$record.tmp" && mv "$record.tmp" "$record"
expect_rejected "a decision dated in the future"

# --- one id, two decisions ---------------------------------------------------
seed_valid_record
printf '\n## D-01 — the same id again (#2)\n\nDecided on: 2026-08-15\nDecided by: x\nChannel: y\n' >>"$record"
expect_rejected "two decisions sharing one id"

# --- a record with nothing in it ---------------------------------------------
# The check must not pass by having nothing to check.
printf '# Deferred owner decisions\n' >"$record"
expect_rejected "a record containing no decisions at all"

# --- a missing record ---------------------------------------------------------
rm -f "$record"
expect_rejected "a record that does not exist"

# --- PENDING: accepted by default, refused under --strict ---------------------
# A drafted entry says out loud that it is unratified. That is honest, so the
# ordinary gate allows it; --strict is what refuses to treat it as authority.
seed_valid_record
sed 's/^Decided by:.*/Decided by: PENDING/' "$record" >"$record.tmp" && mv "$record.tmp" "$record"
if ! run_validator; then
    printf 'a PENDING field was rejected by the ordinary gate, which is where drafts live\n' >&2
    exit 1
fi
if run_validator --strict; then
    printf 'negative fixture unexpectedly accepted: a PENDING field under --strict\n' >&2
    exit 1
fi
fixture_count=$((fixture_count + 1))

printf 'owner-decision negative fixtures: OK (%d rejections)\n' "$fixture_count"
