#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
validator="$repo_root/scripts/verify-root-boundaries.sh"
fixture_root="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-root-boundary.XXXXXX")"
trap 'rm -rf "$fixture_root"' EXIT

mkdir -p "$fixture_root/mobile" "$fixture_root/contracts"

if SUPERNUTRITION_ROOT_BOUNDARIES_ROOT="$fixture_root" "$validator"; then
  printf '%s\n' 'negative fixture unexpectedly accepted a missing preselection root' >&2
  exit 1
fi

mkdir -p "$fixture_root/web/landing" "$fixture_root/backend/mobile-api"
printf '%s\n' 'executable contract fixture' > "$fixture_root/contracts/forbidden.kt"
if SUPERNUTRITION_ROOT_BOUNDARIES_ROOT="$fixture_root" "$validator"; then
  printf '%s\n' 'negative fixture unexpectedly accepted executable contract source' >&2
  exit 1
fi

rm "$fixture_root/contracts/forbidden.kt"
rm -rf "$fixture_root/web/landing"
printf '%s\n' 'not a directory' > "$fixture_root/web/landing"
if SUPERNUTRITION_ROOT_BOUNDARIES_ROOT="$fixture_root" "$validator"; then
  printf '%s\n' 'negative fixture unexpectedly accepted a product-root file' >&2
  exit 1
fi

rm "$fixture_root/web/landing"
mkdir -p "$fixture_root/web/landing"
printf '%s\n' 'implementation(files("../web/landing/build/libs/landing.jar"))' \
  > "$fixture_root/mobile/build.gradle.kts"
if SUPERNUTRITION_ROOT_BOUNDARIES_ROOT="$fixture_root" "$validator"; then
  printf '%s\n' 'negative fixture unexpectedly accepted a cross-product build edge' >&2
  exit 1
fi

rm "$fixture_root/mobile/build.gradle.kts"
printf '%s\n' 'CACHE_KEY=mobile/.gradle/foreign' > "$fixture_root/backend/mobile-api/cache-policy.toml"
if SUPERNUTRITION_ROOT_BOUNDARIES_ROOT="$fixture_root" "$validator"; then
  printf '%s\n' 'negative fixture unexpectedly accepted a cross-product cache edge' >&2
  exit 1
fi

printf '%s\n' 'root-boundary negative fixtures: OK'
