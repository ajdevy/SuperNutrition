#!/usr/bin/env bash
# Deliberate violations of the backend product manifests, each of which
# `backend/mobile-api/quality-check` must refuse.
#
# P-06 — the manifest parse stringified whatever it found, so a quoted lookalike
# satisfied every assertion: `quality_claim = "false"` is a string, not the
# boolean the manifest must declare, and it compared equal all the same. The
# inventory beside it was still read with grep, so a duplicated or malformed
# inventory was interpreted textually.
#
# Every fixture here failed to be rejected before the fix. Each runs against a
# copy of the real product root, so a fixture cannot pass by describing a
# product that does not exist.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
product="backend/mobile-api"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

fixture_count=0

# A copy of the product root, ready to be broken in exactly one way.
fresh_product() {
  local name="$1"
  local dir="$work/$name"
  mkdir -p "$dir"
  # The manifests and the entrypoint are all the parse stage reads; copying the
  # whole tree would make each fixture cost a full install.
  cp "$repo_root/$product/quality-check" "$dir/quality-check"
  cp "$repo_root/$product/product-lifecycle.toml" "$dir/product-lifecycle.toml"
  cp "$repo_root/$product/product-inventory.toml" "$dir/product-inventory.toml"
  chmod +x "$dir/quality-check"
  printf '%s\n' "$dir"
}

expect_rejected_because() {
  local description="$1" expected="$2" dir="$3"
  local output status
  set +e
  output="$("$dir/quality-check" 2>&1)"
  status=$?
  set -e
  if [ "$status" -eq 0 ]; then
    printf 'FIXTURE NOT REJECTED: %s\n' "$description" >&2
    exit 1
  fi
  if ! printf '%s' "$output" | grep -qi -- "$expected"; then
    printf 'FIXTURE REJECTED FOR THE WRONG REASON: %s\n' "$description" >&2
    printf '  expected to mention: %s\n' "$expected" >&2
    printf '  actual: %s\n' "$output" >&2
    exit 1
  fi
  fixture_count=$((fixture_count + 1))
  printf '  rejected: %s\n' "$description"
}

printf 'quality-check manifest negative fixtures\n'

# --- the lifecycle record ----------------------------------------------------

dir="$(fresh_product duplicate-lifecycle)"
printf 'lifecycle = "active"\nlifecycle = "preselection"\n' >"$dir/product-lifecycle.toml"
expect_rejected_because "a lifecycle record declaring lifecycle twice" "valid" "$dir"

dir="$(fresh_product malformed-lifecycle)"
printf 'lifecycle = active\n' >"$dir/product-lifecycle.toml"
expect_rejected_because "a lifecycle record that is not valid TOML" "valid" "$dir"

dir="$(fresh_product missing-lifecycle-field)"
printf 'quality_claim = true\n' >"$dir/product-lifecycle.toml"
expect_rejected_because "a lifecycle record that declares no lifecycle" "valid" "$dir"

# --- quoted lookalikes: the P-06 finding itself ------------------------------

dir="$(fresh_product string-quality-claim)"
python3 - "$dir/product-lifecycle.toml" <<'PY'
import re, sys
path = sys.argv[1]
text = open(path, encoding="utf-8").read()
# A STRING that spells the boolean. The old parse stringified it and compared equal.
text = re.sub(r'^quality_claim\s*=.*$', 'quality_claim = "false"', text, count=1, flags=re.M)
open(path, "w", encoding="utf-8").write(text)
PY
expect_rejected_because "quality_claim written as a quoted string" "bool" "$dir"

dir="$(fresh_product string-source-count)"
python3 - "$dir/product-lifecycle.toml" <<'PY'
import re, sys
path = sys.argv[1]
text = open(path, encoding="utf-8").read()
text = re.sub(
    r'^production_source_count\s*=.*$', 'production_source_count = "0"', text, count=1, flags=re.M
)
open(path, "w", encoding="utf-8").write(text)
PY
expect_rejected_because "production_source_count written as a quoted string" "int" "$dir"

dir="$(fresh_product boolean-source-count)"
python3 - "$dir/product-lifecycle.toml" <<'PY'
import re, sys
path = sys.argv[1]
text = open(path, encoding="utf-8").read()
# bool is a subclass of int in Python: without an explicit check this passed.
text = re.sub(
    r'^production_source_count\s*=.*$', 'production_source_count = true', text, count=1, flags=re.M
)
open(path, "w", encoding="utf-8").write(text)
PY
expect_rejected_because "production_source_count written as a boolean" "boolean" "$dir"

# --- the inventory, which used to be read with grep --------------------------

dir="$(fresh_product duplicate-inventory-lifecycle)"
printf 'lifecycle = "active"\nlifecycle = "preselection"\n' >"$dir/product-inventory.toml"
expect_rejected_because "an inventory declaring lifecycle twice" "valid" "$dir"

dir="$(fresh_product malformed-inventory)"
printf 'lifecycle = active\n' >"$dir/product-inventory.toml"
expect_rejected_because "an inventory that is not valid TOML" "valid" "$dir"

dir="$(fresh_product mismatched-inventory)"
printf 'lifecycle = "preselection"\n' >"$dir/product-inventory.toml"
expect_rejected_because "an inventory whose lifecycle contradicts the record" "disagrees" "$dir"

dir="$(fresh_product nested-inventory-lifecycle)"
# The grep matched a line anywhere in the file, including under another table,
# where it says nothing about the product's own lifecycle.
printf '[some_other_table]\nlifecycle = "active"\n' >"$dir/product-inventory.toml"
expect_rejected_because "an inventory whose lifecycle sits under another table" "valid" "$dir"

printf 'quality-check manifest negative fixtures: OK (%d rejections, each for its own reason)\n' \
  "$fixture_count"
