#!/usr/bin/env bash
# Negative fixtures for scripts/verify-mobile-api-contract.sh.
#
# WHY THIS FILE WAS REWRITTEN
# The verifier grew to seven verification groups. This harness had one fixture,
# and that fixture did not test what it claimed. It flipped `expected =
# "accepted"` to `"rejected"` in the compatibility matrix and asserted a
# rejection — but mutating any file also invalidates the release digest, so the
# verifier stopped at "compatibility digest is stale" and never reached the skew
# rule the fixture was named after. Correct the skew rule to nothing at all and
# the fixture still passed.
#
# Two things fix that. Every mutation is REPINNED — the digest is recomputed and
# written back — so the only thing left to reject is the semantic change. And
# every assertion names the reason it expects, so a rejection for an unrelated
# cause is a failure rather than a pass.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
verifier="${repo_root}/scripts/verify-mobile-api-contract.sh"
work=$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-mobile-api-contract.XXXXXX")
trap 'rm -rf "$work"' EXIT

test -x "$verifier" || {
    printf 'mobile-api verifier is missing or not executable: %s\n' "$verifier" >&2
    exit 1
}

fixture_count=0

fresh_release() {
    local name="$1"
    local root="$work/$name"
    cp -R "${repo_root}/contracts/mobile-api/v1" "$root"
    printf '%s' "$root"
}

# Recomputes the release digest and writes it into the compatibility matrix.
#
# Without this every fixture is answered by verification 6 — "digest is stale" —
# and no fixture ever reaches the rule it was written for.
repin() {
    SUPERNUTRITION_SCRIPT_LIB="${repo_root}/scripts/lib" python3 - "$1" <<'PY'
from pathlib import Path
import os
import re
import sys

sys.path.insert(0, os.environ["SUPERNUTRITION_SCRIPT_LIB"])
from release_digest import compute

root = Path(sys.argv[1])
matrix = root / "compatibility-matrix.toml"
matrix.write_text(
    re.sub(
        r'^digest = "[a-f0-9]{64}"$',
        f'digest = "{compute(root)}"',
        matrix.read_text(),
        count=1,
        flags=re.M,
    )
)
PY
}

expect_rejected_because() {
    local description="$1" expected="$2" root="$3"
    local output status
    set +e
    output="$(SUPERNUTRITION_MOBILE_API_CONTRACT="$root" "$verifier" 2>&1)"
    status=$?
    set -e

    if test "$status" -eq 0; then
        printf 'negative fixture unexpectedly accepted: %s\n' "$description" >&2
        exit 1
    fi
    if ! printf '%s' "$output" | grep -qi -- "$expected"; then
        printf 'fixture "%s" was rejected, but not for its own reason.\n' "$description" >&2
        printf '  expected the message to mention: %s\n' "$expected" >&2
        printf '  actual: %s\n' "$(printf '%s' "$output" | tail -1)" >&2
        exit 1
    fi
    fixture_count=$((fixture_count + 1))
}

mutate() {
    # root, python body operating on `root`
    SUPERNUTRITION_FIXTURE_ROOT="$1" python3 -c "
import os, re, tomllib
from pathlib import Path
root = Path(os.environ['SUPERNUTRITION_FIXTURE_ROOT'])
$2
"
}

# --- positive control -------------------------------------------------------
# An untouched copy must pass, and a repinned copy must also pass. If repinning
# alone were enough to fail, every fixture below would be meaningless.
baseline="$(fresh_release baseline)"
if ! SUPERNUTRITION_MOBILE_API_CONTRACT="$baseline" "$verifier" >/dev/null 2>&1; then
    printf 'positive control failed: an untouched release copy was rejected\n' >&2
    exit 1
fi
repin "$baseline"
if ! SUPERNUTRITION_MOBILE_API_CONTRACT="$baseline" "$verifier" >/dev/null 2>&1; then
    printf 'positive control failed: repinning a release changed its verdict\n' >&2
    exit 1
fi

# --- verification 6: the digest itself --------------------------------------
# Kept deliberately: this is the one fixture that SHOULD be answered by the
# digest rule, and it proves the rule still bites once repinning exists.
root="$(fresh_release stale-digest)"
mutate "$root" "
p = root / 'compatibility-matrix.toml'
p.write_text(re.sub(r'^digest = \"[a-f0-9]{64}\"\$', 'digest = \"' + '0'*64 + '\"', p.read_text(), count=1, flags=re.M))
"
expect_rejected_because "a stale release digest" "digest is stale" "$root"

# --- verification 3: skew pairs ---------------------------------------------
# What the old single fixture was named for, and never reached.
root="$(fresh_release skew-pair)"
mutate "$root" "
p = root / 'compatibility-matrix.toml'
p.write_text(p.read_text().replace('expected = \"accepted\"', 'expected = \"rejected\"', 1))
"
repin "$root"
expect_rejected_because "an inverted compatibility skew pair" "skew pairs" "$root"

# --- verification 3: duplicate gateway code ---------------------------------
root="$(fresh_release duplicate-code)"
mutate "$root" "
p = root / 'error-taxonomy.toml'
text = p.read_text()
first = re.search(r'(?ms)^\[\[error\]\].*?(?=^\[\[error\]\]|\Z)', text).group(0)
p.write_text(text + '\n' + first)
"
repin "$root"
expect_rejected_because "a duplicated gatewayCode" "duplicate gatewayCode" "$root"

# --- verification 2: nutrition in a gateway response ------------------------
# AD-7. The gateway returns evidence, never numbers.
root="$(fresh_release nutrition-leak)"
mutate "$root" "
p = root / 'gateway-response.toml'
p.write_text(p.read_text() + '\n[nutrition]\ncalories = 100\n')
"
repin "$root"
expect_rejected_because "a nutrition field in a gateway response" "nutrition" "$root"

# --- verification 4: consent binding fields ---------------------------------
root="$(fresh_release consent-binding)"
mutate "$root" "
p = root / 'consent-receipt.toml'
p.write_text(re.sub(r'(?m)^name = \"digest\"\$', 'name = \"unrelated\"', p.read_text(), count=1))
"
repin "$root"
expect_rejected_because "a consent receipt missing its payload binding" "binding fields" "$root"

# --- verification 4: recall claimed -----------------------------------------
# AD-8's hardest rule: the gateway may never say bytes were recalled.
root="$(fresh_release recall-claimed)"
mutate "$root" "
p = root / 'transfer-states.toml'
p.write_text(p.read_text().replace('enum = [false]', 'enum = [false, true]', 1))
"
repin "$root"
expect_rejected_because "a transfer state permitting a recall claim" "recall" "$root"

# --- verification 4: market policy must fail closed -------------------------
root="$(fresh_release market-open)"
mutate "$root" "
p = root / 'market-policy.toml'
p.write_text(p.read_text().replace('fail_closed = true', 'fail_closed = false', 1))
"
repin "$root"
expect_rejected_because "a market policy that does not fail closed" "fail-closed" "$root"

# --- verification 5: a fixture rejecting without a reason -------------------
root="$(fresh_release reasonless-rejection)"
mutate "$root" "
p = root / 'fixtures/photo-analysis-consent-expired.toml'
p.write_text(re.sub(r'(?m)^reason = .*\$', '', p.read_text(), count=1))
"
repin "$root"
expect_rejected_because "a rejection fixture with no reason" "rejects without a reason" "$root"

# --- verification 7: a symlink inside a release -----------------------------
#
# Targeted at the symlink branch rather than the non-contract-material branch,
# because the latter is no longer reachable: M13 added a declared-set check to
# verification 6, which runs first and rejects any added file by name. A symlink
# that REPLACES a declared file keeps the declared set intact, so verification 7
# is the only thing left to catch it — and a symlink is the case that matters,
# since it lets bytes outside the release masquerade as part of it.
root="$(fresh_release symlinked-member)"
rm "$root/rate-limit.toml"
ln -s "transfer-states.toml" "$root/rate-limit.toml"
repin "$root"
expect_rejected_because "a symlinked release member" "symlink" "$root"

# --- verification 6: an undeclared file -------------------------------------
# Added with M13: the digest covers the declared set, so a file present but not
# declared means the digest describes something nobody wrote down.
root="$(fresh_release undeclared-file)"
printf 'schema = "surprise"\n' >"$root/surprise.toml"
repin "$root"
expect_rejected_because "a file present in the release but undeclared" "undeclared or missing" "$root"

printf 'mobile-api contract negative fixtures: OK (%d rejections, each for its own reason)\n' "$fixture_count"
