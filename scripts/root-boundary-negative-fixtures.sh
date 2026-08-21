#!/usr/bin/env bash
# Negative fixtures for scripts/verify-root-boundaries.sh.
#
# WHY THIS FILE WAS REWRITTEN
# Every fixture in it was vacuous. The fixture root was seeded with four empty
# directories, and the validator rejects that root outright — "missing protected
# ownership inventory" — before it ever looks at a cross-product edge. So each
# fixture mutated the tree, saw a rejection, and concluded its mutation had been
# caught. Delete every mutation and the suite still passes: the rejections were
# never about them.
#
# Two things fix that, and both are the point of the file. The root is seeded
# into a state the validator ACCEPTS, proved by a positive control. And each
# fixture asserts the failure names its own reason, so a rejection for an
# unrelated cause is a failure rather than a pass.
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
validator="$repo_root/scripts/verify-root-boundaries.sh"

test -x "$validator" || {
  printf 'root boundary validator is missing or not executable: %s\n' "$validator" >&2
  exit 1
}

work="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-root-boundary.XXXXXX")"
trap 'rm -rf "$work"' EXIT

fixture_count=0

# A root the validator accepts. Everything a fixture then does to it is the only
# difference between passing and failing, which is what makes the fixture mean
# something.
seed_valid_root() {
  local root="$1"
  mkdir -p "$root/mobile" "$root/web/landing" "$root/backend/mobile-api" \
    "$root/contracts" "$root/.github"

  cp -R "$repo_root/contracts/root-policy" "$root/contracts/"
  cp "$repo_root/.github/CODEOWNERS" "$root/.github/CODEOWNERS"
  local product
  for product in mobile web/landing backend/mobile-api; do
    cp "$repo_root/$product/product-lifecycle.toml" "$root/$product/product-lifecycle.toml"
  done

  # Every executable the boundary inventory declares, stubbed. The validator
  # checks that they exist and are executable, not what they do.
  local declared
  declared="$(cd "$repo_root" && python3 -c '
import tomllib, pathlib
document = tomllib.loads(pathlib.Path("contracts/root-policy/v1/boundaries.toml").read_text())
print("\n".join(entry["path"] for entry in document.get("executable", [])))
')"
  local path
  for path in $declared mobile/gradlew backend/mobile-api/quality-check; do
    mkdir -p "$root/$(dirname "$path")"
    printf '#!/usr/bin/env bash\nexit 0\n' >"$root/$path"
    chmod +x "$root/$path"
  done
}

fresh_root() {
  local name="$1"
  local root="$work/$name"
  mkdir -p "$root"
  seed_valid_root "$root"
  printf '%s' "$root"
}

# Asserts BOTH that the validator rejected, and that it rejected for the stated
# reason. Without the second half a fixture passes on any failure at all, which
# is exactly how this file came to prove nothing.
expect_rejected_because() {
  local description="$1"
  local expected="$2"
  local root="$3"
  local output status
  set +e
  output="$(SUPERNUTRITION_ROOT_BOUNDARIES_ROOT="$root" "$validator" 2>&1)"
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

expect_accepted() {
  local description="$1"
  local root="$2"
  if ! SUPERNUTRITION_ROOT_BOUNDARIES_ROOT="$root" "$validator" >/dev/null 2>&1; then
    printf 'positive control failed: %s\n' "$description" >&2
    SUPERNUTRITION_ROOT_BOUNDARIES_ROOT="$root" "$validator" 2>&1 | tail -3 >&2
    exit 1
  fi
}

# --- positive control, first ------------------------------------------------
# If this fails, every rejection below is meaningless.
baseline="$(fresh_root baseline)"
expect_accepted "an unmutated, correctly seeded root" "$baseline"

# --- missing preselection root ----------------------------------------------
root="$(fresh_root missing-preselection)"
rm -rf "$root/web/landing"
expect_rejected_because "a missing product root" "web/landing" "$root"

# --- executable material inside contracts/ ----------------------------------
root="$(fresh_root executable-contract)"
printf 'executable contract fixture\n' >"$root/contracts/forbidden.kt"
expect_rejected_because "executable source under contracts/" "contracts" "$root"

# --- a product root that is a file ------------------------------------------
root="$(fresh_root product-root-file)"
rm -rf "$root/web/landing"
printf 'not a directory\n' >"$root/web/landing"
expect_rejected_because "a product root that is a file" "web/landing" "$root"

# --- cross-product build edge -----------------------------------------------
root="$(fresh_root build-edge)"
printf 'implementation(files("../web/landing/build/libs/landing.jar"))\n' \
  >"$root/mobile/build.gradle.kts"
expect_rejected_because "a cross-product build edge" "cross-product edge in mobile" "$root"

# --- cross-product cache edge -----------------------------------------------
root="$(fresh_root cache-edge)"
printf 'CACHE_KEY=mobile/.gradle/foreign\n' >"$root/backend/mobile-api/cache-policy.toml"
expect_rejected_because "a cross-product cache edge" "cross-product edge in backend/mobile-api" "$root"

# --- M17: a BARE cross-root path in a shell file ----------------------------
# Invisible to the general pattern, which needs either a ../ prefix or an opening
# quote — and this is the shape a script actually uses. Measured against the real
# tree before the fix: a file containing exactly this line was reported OK.
root="$(fresh_root bare-shell-path)"
printf '#!/usr/bin/env bash\ncd mobile/build-logic && ./gradlew build\n' \
  >"$root/backend/mobile-api/release.sh"
expect_rejected_because "a bare cross-product path in a shell file" "bare path" "$root"

# --- M17: the same shape in a CI step ---------------------------------------
root="$(fresh_root bare-ci-path)"
mkdir -p "$root/backend/mobile-api/.ci"
printf 'jobs:\n  build:\n    steps:\n      - run: mobile/gradlew assemble\n' \
  >"$root/backend/mobile-api/.ci/build.yml"
expect_rejected_because "a bare cross-product path in a CI file" "bare path" "$root"

# --- M17 positive control: the rule that describes the boundary -------------
# backend/mobile-api/eslint.config.js names mobile/ in its message precisely in
# order to forbid it. This is why the bare-path rule is scoped to shell and CI
# files instead of being folded into the general pattern: folding it in failed
# on that real file immediately.
root="$(fresh_root boundary-lint-rule)"
printf "export default [{ message: 'AD-36: no cross-product source edge to mobile/.' }];\n" \
  >"$root/backend/mobile-api/eslint.config.js"
expect_accepted "a lint rule whose message names the sibling root in order to forbid it" "$root"

# --- M-03: a manifest that does not parse is not a manifest ------------------
# The lifecycle used to be read with sed, so a duplicated key silently took
# whichever line matched first and a malformed file read as the empty string.
# Both then flowed into the reported state, which is what a reader trusts.
root="$(fresh_root boundary-duplicate-lifecycle)"
printf 'lifecycle = "active"\nlifecycle = "preselection"\n' \
  >"$root/backend/mobile-api/product-lifecycle.toml"
expect_rejected_because "a manifest declaring lifecycle twice" "TOML" "$root"

root="$(fresh_root boundary-malformed-lifecycle)"
printf 'lifecycle = active\n' >"$root/backend/mobile-api/product-lifecycle.toml"
expect_rejected_because "a manifest that is not valid TOML" "TOML" "$root"

# --- TL-H1: the inventory's own absence must not be an acceptance -------------
# The executable-enumeration gate and the required-entrypoint checks were both
# wrapped in `if test -f "$boundary_inventory"; then … fi` with no else, so
# deleting the inventory printed OK. That file is EXEMPT from the immutability
# freeze and has no catalog entry, so nothing downstream caught the deletion
# either: removing it disabled the only check that every executable under
# scripts/ is declared, and the only check that the product entrypoints exist.
#
# Three fixtures, because "OK" was returned for three different absences.
root="$(fresh_root boundary-inventory-deleted)"
rm -f "$root/contracts/root-policy/v1/boundaries.toml"
expect_rejected_because "a deleted boundary inventory" "boundary inventory" "$root"

# The deletion is what a real bypass would pair with something to hide.
root="$(fresh_root boundary-inventory-deleted-with-stowaway)"
rm -f "$root/contracts/root-policy/v1/boundaries.toml"
printf '#!/usr/bin/env bash\nexit 0\n' >"$root/scripts/undeclared-executable.sh"
chmod +x "$root/scripts/undeclared-executable.sh"
expect_rejected_because "a deleted inventory hiding an undeclared executable" "boundary inventory" "$root"

root="$(fresh_root boundary-inventory-deleted-missing-entrypoint)"
rm -f "$root/contracts/root-policy/v1/boundaries.toml"
rm -f "$root/mobile/gradlew"
expect_rejected_because "a deleted inventory hiding a removed entrypoint" "boundary inventory" "$root"

# --- the file types a bare cross-root path actually appears in ---------------
# The bare-path scan named the shapes it looked for and then did not look in the
# files that have them: Makefile, Dockerfile, *.gradle(.kts) and *.mjs were all
# outside its include list, while being exactly where a `COPY ../other-product`
# or a sibling-root Gradle reference lives.
for probe in Dockerfile Makefile build.gradle.kts tool.mjs; do
  root="$(fresh_root "boundary-bare-path-$probe")"
  case "$probe" in
    Dockerfile) printf 'FROM node:22\nCOPY mobile/gradlew /app/\n' >"$root/backend/mobile-api/$probe" ;;
    Makefile)   printf 'build:\n\tcd mobile/build-logic && ./gradlew\n' >"$root/backend/mobile-api/$probe" ;;
    # UNQUOTED, so the bare-path scan is what catches it. A quoted form is
    # caught by the general pattern instead, and the fixture would then pass
    # while proving nothing about the include list this test is about.
    *)          printf 'val tool = mobile/gradlew\n' >"$root/backend/mobile-api/$probe" ;;
  esac
  expect_rejected_because "a bare cross-product path in $probe" "bare path" "$root"
done

printf 'root-boundary negative fixtures: OK (%d rejections, each for its own reason)\n' "$fixture_count"
