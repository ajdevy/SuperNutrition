#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
source "$script_dir/lib/quality-common.sh"
temp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/supernutrition-ui-ios"
report_root="$temp_root/report"
mkdir -p "$report_root/debug"

lifecycle="$(lifecycle_value "$repo_root" mobile/product-lifecycle.toml)"
printf 'lifecycle=%s\n' "$lifecycle" >"$report_root/lifecycle.txt"
run_optional_apple_quality() {
  printf 'apple_evidence=good-to-have-unavailable\n' >>"$report_root/lifecycle.txt"
  exec "$repo_root/mobile/gradlew" -p "$repo_root/mobile" \
    qualityCheckApple \
    --dependency-verification=strict \
    --no-daemon \
    --no-configuration-cache
}
if test "$lifecycle" != "active"; then
  exec "$repo_root/mobile/gradlew" -p "$repo_root/mobile" \
    qualityCheckApple \
    --dependency-verification=strict \
    --no-daemon \
    --no-configuration-cache
fi

xcode_version="$(xcodebuild -version 2>/dev/null | sed -n '1p' || true)"
if test "$xcode_version" != "Xcode 26.4.1"; then
  run_optional_apple_quality
fi

runtime_id="com.apple.CoreSimulator.SimRuntime.iOS-26-4"
if ! xcrun simctl list runtimes 2>/dev/null | grep -Fq "$runtime_id ("; then
  run_optional_apple_quality
fi

device_type_id="com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"
if ! xcrun simctl list devicetypes 2>/dev/null | grep -Fq "$device_type_id"; then
  run_optional_apple_quality
fi

simulator_name="SuperNutrition iPhone 17 Pro"
simulator_udid="$(xcrun simctl list devices available | awk -v name="$simulator_name" '$0 ~ name " \(" {gsub(/[()]/, "", $NF); print $NF; exit}')"
if test -z "$simulator_udid"; then
  simulator_udid="$(xcrun simctl create "$simulator_name" "$device_type_id" "$runtime_id")"
fi
xcrun simctl boot "$simulator_udid" 2>/dev/null || true
xcrun simctl bootstatus "$simulator_udid" -b

"$repo_root/mobile/gradlew" -p "$repo_root/mobile" \
  :apps:sharedKit:linkDebugFrameworkIosSimulatorArm64 \
  -Psupernutrition.enableSharedKitFramework=true \
  --dependency-verification=strict \
  --no-daemon \
  --no-configuration-cache

derived_data="$temp_root/DerivedData"
mkdir -p "$temp_root"
xcodebuild clean test -quiet \
  -project "$repo_root/mobile/apps/iosApp/SuperNutritionUIAcceptance.xcodeproj" \
  -scheme SuperNutritionUIAcceptance \
  -destination "platform=iOS Simulator,id=$simulator_udid" \
  -derivedDataPath "$derived_data" \
  SHARED_KIT_FRAMEWORK_DIR="$repo_root/mobile/apps/sharedKit/build/bin/iosSimulatorArm64/debugFramework" \
  CODE_SIGNING_ALLOWED=NO

ios_app="$(find "$derived_data" -type d -path '*/Debug-iphonesimulator/SuperNutritionUIAcceptance.app' -print -quit)"
test -n "$ios_app" || {
  printf 'iOS UI acceptance app was not produced\n' >&2
  exit 1
}
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
