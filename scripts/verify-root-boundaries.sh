#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="${SUPERNUTRITION_ROOT_BOUNDARIES_ROOT:-$(cd "$script_dir/.." && pwd)}"

for required_tool in find grep python3; do
  command -v "$required_tool" >/dev/null 2>&1 || {
    printf 'root-boundaries: missing required tool: %s\n' "$required_tool" >&2
    exit 1
  }
done

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
  -name '*.py' -o -name '*.js' -o -name '*.ts' -o -name '*.sh' -o \
  -name '*.go' -o -name '*.rb' -o -name '*.rs' -o -name '*.cpp' -o \
  -name '*.cc' -o -name '*.c' -o -name '*.h' -o -name '*.hpp' \
\) -print -quit | grep -q .; then
  printf 'contracts contains executable or product source\n' >&2
  exit 1
fi
if find "$repo_root/contracts" -type f -perm -111 -print -quit | grep -q .; then
  printf 'contracts contains an executable file\n' >&2
  exit 1
fi

# Every executable under the repository-owned scripts root must be explicitly
# enumerated by the protected root-policy inventory.
boundary_inventory="$repo_root/contracts/root-policy/v1/boundaries.toml"
# TL-H1 — the absence of the inventory is a failure, not a skip.
#
# This was `if test -f …; then … fi` with no else, so deleting the file printed
# "root-boundaries: OK": the executable-enumeration gate and the
# required-entrypoint checks both vanished with it. The path is exempt from the
# immutability freeze and has no catalog entry, so nothing downstream noticed
# either — deleting one file disabled the check and kept the green light.
#
# Mirrors the ownership.toml check below, which was always written this way.
test -f "$boundary_inventory" || {
  printf 'missing protected boundary inventory: %s\n' \
    "${boundary_inventory#"$repo_root/"}" >&2
  exit 1
}
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
# contracts/. A textual reference to another product root is therefore a
# forbidden source, build, runtime, generated-output, cache, or deployment
# edge. Generated local build metadata is excluded from this read-only scan;
# product-owned manifests and checked-in configuration remain covered.
PRODUCT_ROOTS=(mobile web/landing backend/mobile-api)

# AD-36 forbids source imports, workspaces, package/project dependencies,
# generated-source writes, runtime calls/configuration, build-task edges, shared
# output directories, and deployment/artifact dependencies across product roots.
# Every one of those is a PATH reference. Matching a bare sibling-root name
# instead flags prose and, decisively, the lint rule that enforces this very
# boundary, which cannot express the forbidden pattern without containing it.
path_edge_pattern() {
  printf '(\\.{1,2}/)+(%s)([/"'\''`[:space:],;)]|$)|["'\''`](%s)/' "$1" "$1"
}

# A BARE path reference — `cd mobile/build-logic`, `backend/mobile-api/quality-check`,
# `mobile/gradlew` in a workflow step — is invisible to the pattern above: one
# alternation needs a ../ prefix, the other an opening quote. Those are exactly
# the shapes a shell script or CI step uses, so the rule was blind to cross-root
# edges in the files most likely to create them.
#
# Applied ONLY to shell, CI and plain configuration files. In those a bare path
# is something that runs or something a tool resolves;
# in source and prose it is a string, and matching there flags the lint rule that
# enforces this very boundary — which cannot describe the forbidden pattern
# without containing it. Measured, not assumed: adding this alternation to the
# general pattern immediately failed on backend/mobile-api/eslint.config.js,
# whose message names mobile/ precisely in order to forbid it.
bare_path_pattern() {
  printf '(^|[[:space:]=:(,;])(%s)/' "$1"
}

# The config extensions were added after the rewritten negative-fixture harness
# showed a cross-product CACHE edge in a .toml going completely undetected — a
# gap the old vacuous fixtures had concealed. Checked first: no file under any
# product root legitimately carries such a reference today.
#
# Build and container files were missing from that list, and they are the file
# types where a bare cross-root path is most likely and most consequential:
# `Makefile` and `Dockerfile` carry `COPY ../other-product`-shaped edges,
# `*.gradle.kts` is how a Gradle build reaches a sibling root, and `*.mjs` is
# ordinary tooling here. The scan named the shapes it was looking for and then
# did not look in the files that have them.
search_executable_files() {
  local pattern="$1"
  local product_path="$2"
  if command -v rg >/dev/null 2>&1; then
    rg --hidden -n -S \
      --glob '!**/build/**' \
      --glob '!**/node_modules/**' \
      --glob '*.sh' --glob '*.bash' --glob '*.yml' --glob '*.yaml' \
      --glob '*.toml' --glob '*.properties' --glob '*.cfg' --glob '*.ini' \
      --glob '*.gradle' --glob '*.gradle.kts' --glob '*.mjs' --glob '*.cjs' \
      --glob 'Makefile' --glob 'makefile' --glob 'GNUmakefile' \
      --glob 'Dockerfile' --glob 'Dockerfile.*' --glob '*.dockerfile' \
      "$pattern" "$product_path"
  else
    grep -R -n -I -E \
      --exclude-dir=build \
      --exclude-dir=node_modules \
      --include='*.sh' --include='*.bash' --include='*.yml' --include='*.yaml' \
      --include='*.toml' --include='*.properties' --include='*.cfg' --include='*.ini' \
      --include='*.gradle' --include='*.gradle.kts' --include='*.mjs' --include='*.cjs' \
      --include='Makefile' --include='makefile' --include='GNUmakefile' \
      --include='Dockerfile' --include='Dockerfile.*' --include='*.dockerfile' \
      "$pattern" "$product_path"
  fi
}

search_product_root() {
  local pattern="$1"
  local product_path="$2"
  if command -v rg >/dev/null 2>&1; then
    rg --hidden -n -S \
      --glob '!**/build/**' \
      --glob '!**/.gradle/**' \
      --glob '!**/.idea/**' \
      --glob '!**/.kotlin/**' \
      --glob '!**/node_modules/**' \
      --glob '!**/docs/**' \
      --glob '!**/*.jar' \
      --glob '!**/*.bin' \
      "$pattern" "$product_path"
  else
    grep -R -n -I -E \
      --exclude-dir=build \
      --exclude-dir=.gradle \
      --exclude-dir=.idea \
      --exclude-dir=.kotlin \
      --exclude-dir=node_modules \
      --exclude-dir=docs \
      --exclude='*.jar' \
      --exclude='*.bin' \
      "$pattern" "$product_path"
  fi
}

for product_root in "${PRODUCT_ROOTS[@]}"; do
  case "$product_root" in
    mobile)
      forbidden_pattern="$(path_edge_pattern 'web/landing|backend/mobile-api')"
      bare_pattern="$(bare_path_pattern 'web/landing|backend/mobile-api')"
      ;;
    web/landing)
      forbidden_pattern="$(path_edge_pattern 'mobile|backend/mobile-api')"
      bare_pattern="$(bare_path_pattern 'mobile|backend/mobile-api')"
      ;;
    backend/mobile-api)
      forbidden_pattern="$(path_edge_pattern 'mobile|web/landing')"
      bare_pattern="$(bare_path_pattern 'mobile|web/landing')"
      ;;
  esac

  if search_product_root "$forbidden_pattern" "$repo_root/$product_root"; then
    printf 'forbidden cross-product edge in %s\n' "$product_root" >&2
    exit 1
  fi

  if search_executable_files "$bare_pattern" "$repo_root/$product_root"; then
    printf 'forbidden cross-product edge in %s (bare path in a shell or CI file)\n' "$product_root" >&2
    exit 1
  fi
done

mobile_lifecycle="$({
  REPO_ROOT="$repo_root" python3 - <<'PY'
import os
import tomllib
from pathlib import Path

path = Path(os.environ["REPO_ROOT"]) / "mobile/product-lifecycle.toml"
if not path.is_file():
    print("scaffold-pending")
else:
    try:
        data = tomllib.loads(path.read_text(encoding="utf-8"))
    except (OSError, tomllib.TOMLDecodeError) as error:
        raise SystemExit(f"root-boundaries: invalid mobile lifecycle TOML: {error}") from error
    lifecycle = data.get("lifecycle")
    if not isinstance(lifecycle, str) or not lifecycle:
        raise SystemExit("root-boundaries: mobile lifecycle must be a non-empty string")
    print(lifecycle)
PY
})"
# M-03 — this read the lifecycle with sed, which matches text rather than TOML.
# A duplicated key, different spacing, or a `lifecycle` nested under another
# table all produced a value the manifest does not actually declare, and the
# reported state is what a reader trusts. Parsed properly, and a manifest that
# will not parse is an error rather than an empty string.
read_lifecycle() {
  local manifest="$repo_root/$1/product-lifecycle.toml"
  if ! test -f "$manifest"; then
    printf 'absent'
    return
  fi
  python3 - "$manifest" <<'PY_LIFECYCLE'
import sys, tomllib

path = sys.argv[1]
try:
    with open(path, "rb") as handle:
        doc = tomllib.load(handle)
except tomllib.TOMLDecodeError as error:
    raise SystemExit(f"root-boundaries: {path} is not valid TOML: {error}")

lifecycle = doc.get("lifecycle")
if not isinstance(lifecycle, str) or not lifecycle:
    raise SystemExit(f"root-boundaries: {path} declares no lifecycle")
print(lifecycle)
PY_LIFECYCLE
}

# Captured before printing: a command substitution inside printf's argument list
# swallows the exit status, so a manifest that fails to parse would have been
# reported as an empty lifecycle in an OK line.
web_lifecycle="$(read_lifecycle web/landing)" || exit 1
backend_lifecycle="$(read_lifecycle backend/mobile-api)" || exit 1

printf 'root-boundaries: OK (mobile %s; web %s; backend %s)\n' \
  "$mobile_lifecycle" "$web_lifecycle" "$backend_lifecycle"
