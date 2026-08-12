#!/usr/bin/env bash
# Negative fixtures for the v3 schema-to-binding check.
#
# The gap this closes is a rename that no gate notices, so the fixtures are
# renames: one on each side, plus the field list. A verifier that only passes on
# the happy path proves nothing about the drift it claims to catch.
set -euo pipefail

repo_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
verifier="${repo_root}/scripts/verify-inference-v3-binding.sh"

if test ! -x "$verifier"; then
    echo "inference v3 binding verifier is missing or not executable: $verifier" >&2
    exit 1
fi

work=$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-inference-v3-binding.XXXXXX")
trap 'rm -rf "$work"' EXIT

binding_source="${repo_root}/mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference"

seed() {
    local name="$1"
    mkdir -p "$work/$name"
    cp -R "${repo_root}/contracts/inference/v3" "$work/$name/contract"
    cp -R "$binding_source" "$work/$name/binding"
}

run_case() {
    local name="$1"
    SUPERNUTRITION_INFERENCE_V3_CONTRACT="$work/$name/contract" \
        SUPERNUTRITION_INFERENCE_V3_BINDING="$work/$name/binding" \
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
    local path="$1" before="$2" after="$3"
    python3 - "$path" "$before" "$after" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
if sys.argv[2] not in text:
    raise SystemExit(f"fixture setup failed: {sys.argv[2]!r} not found in {path}")
path.write_text(text.replace(sys.argv[2], sys.argv[3], 1))
PY
}

# Positive control. Without it every rejection below could be proving only that
# the copied tree is broken.
seed baseline
if ! run_case baseline >/dev/null; then
    echo "positive control failed: an unmutated copy was rejected" >&2
    exit 1
fi

# --- a wire value renamed in Kotlin -----------------------------------------
# The precise failure the check exists for: renaming a @SerialName breaks no
# Kotlin test, because the only encoded bytes any test asserts are the canonical
# fixture's, and that fixture carries a different bucket. A rename violates both
# directions at once — the old value is orphaned and the new one unagreed — and
# the orphan is reported first.
seed kotlin_rename
replace_once "$work/kotlin_rename/binding/InferenceTelemetryV3.kt" '@SerialName("over-5s")' '@SerialName("over-30s")'
expect_rejected_because "a bucket renamed in the binding" kotlin_rename "which TelemetryDurationBucketV3 cannot emit"

# --- each direction on its own ----------------------------------------------
# The rename above would pass a check that only looked one way, so both
# directions get a case that violates it alone.
seed kotlin_extra
replace_once "$work/kotlin_extra/binding/InferenceTelemetryV3.kt" '    @SerialName("over-5s")
    OVER_5S,
}' '    @SerialName("over-5s")
    OVER_5S,

    @SerialName("over-30s")
    OVER_30S,
}'
expect_rejected_because "a binding value the wire never agreed to" kotlin_extra "the wire never agreed to that value"

seed schema_removed
replace_once "$work/schema_removed/contract/schema.toml" 'resource_bucket = ["low", "moderate", "high"]' 'resource_bucket = ["low", "moderate"]'
expect_rejected_because "a declared value dropped while the binding still emits it" schema_removed "the wire never agreed to that value"

# --- a vocabulary value nothing can emit ------------------------------------
seed unemittable
replace_once "$work/unemittable/contract/schema.toml" 'route = ["local", "local-text", "remote"]' 'route = ["local", "local-text", "remote", "hybrid"]'
expect_rejected_because "a route declared but unemittable" unemittable "a vocabulary nothing emits is a claim"

# --- the modality discriminator ---------------------------------------------
seed modality
replace_once "$work/modality/contract/schema.toml" 'values = ["photo", "text"]' 'values = ["photo", "text", "voice"]'
expect_rejected_because "a modality the binding cannot represent" modality "modality vocabulary disagrees"

# --- the field list, reordered ----------------------------------------------
# canonical_json is true, so the order is part of the bytes rather than a
# formatting preference.
seed field_order
replace_once "$work/field_order/contract/schema.toml" '  "engineId",
  "modelVersion",' '  "modelVersion",
  "engineId",'
expect_rejected_because "the emitted field list reordered" field_order "field list disagrees"

# --- a property added to the event without declaring it ---------------------
seed field_added
replace_once "$work/field_added/binding/InferenceTelemetryV3.kt" '    val modelVersion: String,
)' '    val modelVersion: String,
    val deviceModel: String,
)'
expect_rejected_because "a field emitted without appearing in the allowlist" field_added "field list disagrees"

echo "inference v3 binding negative fixtures: OK"
