#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
max_workers="${SUPERNUTRITION_MAX_WORKERS:-1}"

usage() {
  cat >&2 <<'USAGE'
Usage: scripts/run-local-ci.sh [jvm|docker|android|ios|all]

  jvm      Run the exact reduced GitHub quality/report job locally (default).
  docker   Build and run the same JVM/report gate in Linux Docker.
  android  Run the Android instrumentation and Maestro lane locally.
  ios      Run the required iOS XCTest and Maestro lane locally.
  all      Run jvm, android, and ios in sequence.

The Android and iOS modes require their native toolchains and devices/simulators.
USAGE
}

run_jvm() {
  "$script_dir/verify-inference-contract.sh"
  "$script_dir/inference-contract-negative-fixtures.sh"
  "$script_dir/verify-root-boundaries.sh"
  "$script_dir/verify-ci-policy.sh"
  "$script_dir/root-boundary-negative-fixtures.sh"
  # The immutability and catalog gates ran only in GitHub Actions, on
  # pull-request events. That is the one adapter this repository treats as
  # optional, so the sole automated enforcement of "published releases are
  # immutable" was reachable only through the path a direct push to main skips
  # entirely — the combination that made TL-H3 exploitable. They are local gates
  # now, where the policy says authority lives.
  "$script_dir/verify-owner-decisions.sh"
  "$script_dir/owner-decisions-negative-fixtures.sh"
  "$script_dir/verify-release-immutability.sh"
  "$script_dir/release-immutability-negative-fixtures.sh"
  "$script_dir/verify-release-catalog.sh"
  "$script_dir/secret-scan-fixtures.sh"
  python3 -m unittest discover -s "$script_dir/tests" -p 'test_*.py'

  local site_dir
  site_dir="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-site.XXXXXX")"
  "$repo_root/mobile/gradlew" -p "$repo_root/mobile" \
    mobileUnitCoverage mobileCoverageReports \
    -Psupernutrition.runJvmCoverage=true \
    --dependency-verification=strict \
    --no-daemon \
    --configuration-cache \
    --max-workers="$max_workers"
  python3 "$script_dir/build_coverage_site.py" --output-dir "$site_dir"
  python3 "$script_dir/update_readme_metrics.py" \
    --readme "$repo_root/README.md" \
    --site-dir "$site_dir" \
    --check
  printf 'local-ci: JVM quality and README report gate passed\n'
}

run_android() {
  "$script_dir/run-ui-journeys-android.sh"
  printf 'local-ci: Android UI gate passed\n'
}

run_docker() {
  "$script_dir/run-local-ci-docker.sh"
  printf 'local-ci: Docker JVM gate passed\n'
}

run_ios() {
  SUPERNUTRITION_REQUIRE_UI_TESTS=1 "$script_dir/run-ui-journeys-ios.sh"
  printf 'local-ci: iOS UI gate passed\n'
}

mode="${1:-jvm}"
case "$mode" in
  jvm)
    run_jvm
    ;;
  docker)
    run_docker
    ;;
  android)
    run_android
    ;;
  ios)
    run_ios
    ;;
  all)
    run_jvm
    run_android
    run_ios
    ;;
  -h|--help)
    usage
    ;;
  *)
    usage
    exit 2
    ;;
esac
