#!/usr/bin/env bash
# Negative fixtures for the v3 schema-to-Kotlin binding check.
set -euo pipefail

repo_root=$(cd "$(dirname "$BASH_SOURCE")/.." && pwd)
verifier="$repo_root/scripts/verify-inference-v3-binding.sh"
tmp_dir=$(printenv TMPDIR || true)
if test -z "$tmp_dir"; then
    tmp_dir=/tmp
fi
work=$(mktemp -d "$tmp_dir/supernutrition-inference-v3-binding.XXXXXX")
trap 'rm -rf "$work"' EXIT
binding_source="$repo_root/mobile/domain/inference/src/commonMain/kotlin/com/supernutrition/domain/inference"

seed() {
    local name="$1"
    mkdir -p "$work/$name"
    cp -R "$repo_root/contracts/inference/v3" "$work/$name/contract"
    cp -R "$binding_source" "$work/$name/binding"
}

run_case() {
    local name="$1"
    SUPERNUTRITION_INFERENCE_V3_CONTRACT="$work/$name/contract" \
        SUPERNUTRITION_INFERENCE_V3_BINDING="$work/$name/binding" "$verifier"
}

if ! "$verifier" >/dev/null; then
    echo "v3 binding positive control failed" >&2
    exit 1
fi

seed renamed
python3 - "$work/renamed/binding/InferenceTelemetryV3.kt" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
text = path.read_text()
needle = '@SerialName("over-5s")'
if needle not in text:
    raise SystemExit("binding fixture setup failed")
path.write_text(text.replace(needle, '@SerialName("over-30s")', 1))
PY
if run_case renamed >/dev/null 2>&1; then
    echo "binding rename unexpectedly passed" >&2
    exit 1
fi

seed schema
python3 - "$work/schema/contract/schema.toml" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
path.write_text(path.read_text().replace(
    'route = ["local", "local-text", "remote"]',
    'route = ["local", "local-text", "remote", "hybrid"]',
    1,
))
PY
if run_case schema >/dev/null 2>&1; then
    echo "schema vocabulary addition unexpectedly passed" >&2
    exit 1
fi

echo "inference v3 binding negative fixtures: OK"
