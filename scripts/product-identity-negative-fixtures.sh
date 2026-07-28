#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
validator="$repo_root/scripts/verify-product-identity.sh"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-identity.XXXXXX")"
trap 'rm -rf "$fixture_root"' EXIT

expect_rejected() {
  local name="$1"
  local contract="$2"
  if SUPERNUTRITION_IDENTITY_CONTRACT="$contract" "$validator" >"$fixture_root/$name.out" 2>&1; then
    printf 'identity fixture unexpectedly accepted: %s\n' "$name" >&2
    cat "$fixture_root/$name.out" >&2
    exit 1
  fi
}

cp "$repo_root/contracts/product-identity/v1/metadata.toml" "$fixture_root/wrong-store-title.toml"
python3 - "$fixture_root/wrong-store-title.toml" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
path.write_text(text.replace("SuperNutrition AI nutritionist", "SuperNutrition", 1), encoding="utf-8")
PY
expect_rejected wrong-store-title "$fixture_root/wrong-store-title.toml"

cp "$repo_root/contracts/product-identity/v1/metadata.toml" "$fixture_root/visible-store-leak.toml"
python3 - "$fixture_root/visible-store-leak.toml" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
path.write_text(text.replace('in_app_name = "SuperNutrition"', 'in_app_name = "SuperNutrition AI nutritionist"', 1), encoding="utf-8")
PY
expect_rejected visible-store-leak "$fixture_root/visible-store-leak.toml"

printf '%s\n' 'product-identity negative fixtures: OK'
