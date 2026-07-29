#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="${SUPERNUTRITION_ROOT_BOUNDARIES_ROOT:-$(cd "$script_dir/.." && pwd)}"

for required in mobile contracts web/landing backend/mobile-api; do
  test -d "$repo_root/$required" || {
    printf 'missing required root: %s\n' "$required" >&2
    exit 1
  }
done

# Web and backend intentionally remain preselection roots until their own
# toolchain stories land. If materialized, they must use exactly these paths.
for product_root in web/landing backend/mobile-api; do
  if test -e "$repo_root/$product_root" && ! test -d "$repo_root/$product_root"; then
    printf 'product root is not a directory: %s\n' "$product_root" >&2
    exit 1
  fi
done

# Contracts are data-only. Executable or product-source extensions in this root
# would turn a released contract into a shared implementation dependency.
if find "$repo_root/contracts" -type f \( \
  -name '*.kt' -o -name '*.kts' -o -name '*.java' -o -name '*.swift' -o \
  -name '*.py' -o -name '*.js' -o -name '*.ts' -o -name '*.sh' \
\) -print -quit | grep -q .; then
  printf 'contracts contains executable or product source\n' >&2
  exit 1
fi

# Every executable under the repository-owned scripts root must be explicitly
# enumerated by the protected root-policy inventory.
boundary_inventory="$repo_root/contracts/root-policy/v1/boundaries.toml"
if test -f "$boundary_inventory"; then
  while IFS= read -r -d '' executable; do
    relative="${executable#"$repo_root/"}"
    if ! grep -Fq "path = \"$relative\"" "$boundary_inventory"; then
      printf 'executable is missing from root-policy inventory: %s\n' "$relative" >&2
      exit 1
    fi
  done < <(find "$repo_root/scripts" -type f -perm -111 -print0)

  for executable in mobile/gradlew web/landing/quality-check backend/mobile-api/quality-check; do
    test -x "$repo_root/$executable" || {
      printf 'required product entrypoint is not executable: %s\n' "$executable" >&2
      exit 1
    }
    if ! grep -Fq "path = \"$executable\"" "$boundary_inventory"; then
      printf 'product entrypoint is missing from root-policy inventory: %s\n' "$executable" >&2
      exit 1
    fi
  done
fi

ownership_inventory="$repo_root/contracts/root-policy/v1/ownership.toml"
codeowners_file="$repo_root/.github/CODEOWNERS"
test -f "$ownership_inventory" || {
  printf 'missing protected ownership inventory\n' >&2
  exit 1
}
test -f "$codeowners_file" || {
  printf 'missing CODEOWNERS configuration\n' >&2
  exit 1
}
OWNERSHIP_INVENTORY="$ownership_inventory" CODEOWNERS_FILE="$codeowners_file" python3 - <<'PY'
import os
import tomllib
from pathlib import Path

inventory = tomllib.loads(Path(os.environ["OWNERSHIP_INVENTORY"]).read_text(encoding="utf-8"))
principals = inventory.get("principal", [])
if inventory.get("status") != "local-policy-only":
    raise SystemExit("ownership inventory must declare local-policy-only status")
if inventory.get("external_gate") != "optional-hosting":
    raise SystemExit("external hosting enforcement must remain optional")
ids = [entry.get("id") for entry in principals]
owners = [entry.get("codeowner") for entry in principals]
if not ids or len(ids) != len(set(ids)) or len(owners) != len(set(owners)):
    raise SystemExit("ownership principals and codeowners must be distinct and nonempty")
codeowners = Path(os.environ["CODEOWNERS_FILE"]).read_text(encoding="utf-8")
for entry in principals:
    if entry["codeowner"] not in codeowners:
        raise SystemExit(f"CODEOWNERS is missing {entry['codeowner']}")
PY

# Product roots may integrate only through immutable data-only releases under
# contracts/. AD-36 forbids source imports, workspaces, package/project
# dependencies, generated-source writes, runtime calls/configuration,
# build-task edges, shared output directories, and deployment/artifact
# dependencies across product roots.
#
# Every one of those is a PATH reference. Matching any textual occurrence of a
# sibling root's name instead flags prose, and — more importantly — flags the
# lint rule that enforces this very boundary, which cannot express the forbidden
# pattern without containing it. So the scan matches path-shaped references:
# a relative traversal into a sibling root, or a quoted path beginning at one.
#
# Generated local build metadata is excluded from this read-only scan;
# product-owned manifests and checked-in configuration remain covered.
path_edge_pattern() {
  # $1 is an alternation of sibling root paths.
  printf '(\\.{1,2}/)+(%s)([/"'\''`[:space:],;)]|$)|["'\''`](%s)/' "$1" "$1"
}

PRODUCT_ROOTS=(mobile web/landing backend/mobile-api)
for product_root in "${PRODUCT_ROOTS[@]}"; do
  case "$product_root" in
    mobile)
      forbidden_pattern="$(path_edge_pattern 'web/landing|backend/mobile-api')"
      ;;
    web/landing)
      forbidden_pattern="$(path_edge_pattern 'mobile|backend/mobile-api')"
      ;;
    backend/mobile-api)
      forbidden_pattern="$(path_edge_pattern 'mobile|web/landing')"
      ;;
  esac

  # AD-36 forbids source imports, workspaces, package/project dependencies,
  # generated-source writes, runtime calls/configuration, build-task edges,
  # shared output directories, and deployment/artifact dependencies across
  # product roots. Non-executable Markdown under a product's docs/ cannot
  # express any of those, and architecture documentation must be able to name
  # the boundary it is documenting, so it is excluded from this scan. Every
  # other path in the product root, including all checked-in configuration and
  # manifests, remains covered.
  if rg --hidden -n -S \
    --glob '!**/build/**' \
    --glob '!**/.gradle/**' \
    --glob '!**/.idea/**' \
    --glob '!**/.kotlin/**' \
    --glob '!**/node_modules/**' \
    --glob '!**/docs/**/*.md' \
    --glob '!**/*.jar' \
    --glob '!**/*.bin' \
    "$forbidden_pattern" "$repo_root/$product_root"; then
    printf 'forbidden cross-product edge in %s\n' "$product_root" >&2
    exit 1
  fi
done

mobile_lifecycle="scaffold-pending"
if test -f "$repo_root/mobile/product-lifecycle.toml"; then
  mobile_lifecycle="$(sed -n 's/^lifecycle = "\([^"]*\)"$/\1/p' "$repo_root/mobile/product-lifecycle.toml" | head -n 1)"
fi
read_lifecycle() {
  if test -f "$repo_root/$1/product-lifecycle.toml"; then
    sed -n 's/^lifecycle = "\([^"]*\)"$/\1/p' "$repo_root/$1/product-lifecycle.toml" | head -n 1
  else
    printf 'absent'
  fi
}

web_lifecycle="$(read_lifecycle web/landing)"
backend_lifecycle="$(read_lifecycle backend/mobile-api)"

printf 'root-boundaries: OK (mobile %s; web %s; backend %s)\n' \
  "$mobile_lifecycle" "$web_lifecycle" "$backend_lifecycle"
