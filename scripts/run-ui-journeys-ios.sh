#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
source "$script_dir/lib/quality-common.sh"
temp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/supernutrition-ui-ios"
report_root="$temp_root/report"
mkdir -p "$report_root/debug"
require_ui_tests="${SUPERNUTRITION_REQUIRE_UI_TESTS:-0}"
case "$require_ui_tests" in
  0|1) ;;
  *)
    printf 'SUPERNUTRITION_REQUIRE_UI_TESTS must be 0 or 1\n' >&2
    exit 1
    ;;
esac

lifecycle="$(lifecycle_value "$repo_root" mobile/product-lifecycle.toml)"
printf 'lifecycle=%s\n' "$lifecycle" >"$report_root/lifecycle.txt"
run_optional_apple_quality() {
  if test "$require_ui_tests" = "1"; then
    printf 'required iOS UI tests cannot run with the available Apple toolchain\n' >&2
    exit 1
  fi
  printf 'apple_evidence=good-to-have-unavailable\n' >>"$report_root/lifecycle.txt"
  exec "$repo_root/mobile/gradlew" -p "$repo_root/mobile" \
    qualityCheckApple \
    --dependency-verification=strict \
    --no-daemon \
    --no-configuration-cache
}
if test "$lifecycle" != "active"; then
  run_optional_apple_quality
fi

xcode_version="$(xcodebuild -version 2>/dev/null | sed -n '1p' || true)"
if test "$xcode_version" != "Xcode 26.4.1"; then
  run_optional_apple_quality
fi

runtime_id="com.apple.CoreSimulator.SimRuntime.iOS-26-4"
if ! xcrun simctl list runtimes 2>/dev/null | awk -v runtime_id="$runtime_id" '$0 ~ runtime_id && $0 !~ /unavailable/ {found=1} END {exit !found}'; then
  run_optional_apple_quality
fi

device_type_id="com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"
if ! xcrun simctl list devicetypes 2>/dev/null | grep -Fq "$device_type_id"; then
  run_optional_apple_quality
fi

simulator_name="SuperNutrition iPhone 17 Pro"
simulator_udid="$(xcrun simctl list devices available | awk -v name="$simulator_name" 'index($0, name " (") {print; exit}' | sed -E 's/.*\(([0-9A-F-]{36})\).*/\1/')"
if test -z "$simulator_udid"; then
  simulator_udid="$(xcrun simctl create "$simulator_name" "$device_type_id" "$runtime_id")"
fi
xcrun simctl boot "$simulator_udid" 2>/dev/null || true
xcrun simctl bootstatus "$simulator_udid" -b

host_arch="$(uname -m)"
case "$host_arch" in
  arm64)
    kotlin_target="iosSimulatorArm64"
    gradle_target="IosSimulatorArm64"
    enable_intel_x64=false
    ;;
  x86_64)
    kotlin_target="iosX64"
    gradle_target="IosX64"
    enable_intel_x64=true
    ;;
  *)
    printf 'Unsupported macOS host architecture: %s\n' "$host_arch" >&2
    exit 1
    ;;
esac

"$repo_root/mobile/gradlew" -p "$repo_root/mobile" \
  ":apps:sharedKit:linkDebugFramework${gradle_target}" \
  -Psupernutrition.enableIosDeviceTarget=true \
  -Psupernutrition.enableTestingAppleTargets=true \
  -Psupernutrition.enableIntelIosX64="$enable_intel_x64" \
  -Psupernutrition.enableSharedKitFramework=true \
  --dependency-verification=strict \
  --no-daemon \
  --no-configuration-cache

derived_data="$temp_root/DerivedData"
mkdir -p "$temp_root"
result_bundle="$report_root/SuperNutritionUIAcceptance-${GITHUB_RUN_ID:-local}-$(date +%s).xcresult"
xcodebuild clean test -quiet \
  -project "$repo_root/mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj" \
  -scheme SuperNutritionUIAcceptance \
  -destination "platform=iOS Simulator,id=$simulator_udid,arch=$host_arch" \
  -derivedDataPath "$derived_data" \
  -resultBundlePath "$result_bundle" \
  SHARED_KIT_FRAMEWORK_DIR="$repo_root/mobile/apps/sharedKit/build/bin/$kotlin_target/debugFramework" \
  ARCHS="$host_arch" \
  ONLY_ACTIVE_ARCH=YES \
  CODE_SIGNING_ALLOWED=NO

ios_app="$(find "$derived_data" -type d -path '*/Debug-iphonesimulator/SuperNutritionUIAcceptance.app' -print -quit)"
test -n "$ios_app" || {
  printf 'iOS UI acceptance app was not produced\n' >&2
  exit 1
}
xcrun xcresulttool get test-results summary \
  --path "$result_bundle" \
  >"$report_root/xctest-summary.json"
python3 "$repo_root/scripts/verify_ui_test_reports.py" \
  --xcresult-summary "$report_root/xctest-summary.json" \
  --label "iOS XCTest"
xcrun simctl install "$simulator_udid" "$ios_app"

maestro_bin="$(ensure_maestro_cli "$repo_root" "$temp_root")"
"$maestro_bin" check-syntax \
  "$repo_root/mobile/.maestro/photo-analysis/photo-analysis-capture-recovery.yaml"

"$maestro_bin" --device "$simulator_udid" test \
  "$repo_root/mobile/.maestro/photo-analysis" \
  --env APP_ID=com.supernutrition.ios.uiacceptance \
  --format junit \
  --output "$report_root/junit.xml" \
  --test-output-dir "$report_root/debug"
python3 "$repo_root/scripts/verify_ui_test_reports.py" \
  --junit-root "$report_root" \
  --label "iOS Maestro"
