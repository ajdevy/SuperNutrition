#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
temp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/supernutrition-ui-ios"
report_root="$temp_root/report"
mkdir -p "$report_root/debug"

lifecycle="$(sed -n 's/^lifecycle = "\([^"]*\)"$/\1/p' "$repo_root/mobile/product-lifecycle.toml" | head -n 1)"
printf 'lifecycle=%s\n' "$lifecycle" >"$report_root/lifecycle.txt"
if test "$lifecycle" != "active"; then
  exec "$repo_root/mobile/gradlew" -p "$repo_root/mobile" \
    qualityCheckApple \
    --dependency-verification=strict \
    --no-daemon \
    --no-configuration-cache
fi

test "$(xcodebuild -version | sed -n '1p')" = "Xcode 26.4.1" || {
  printf 'Xcode 26.4.1 is required for the active Apple UI lane\n' >&2
  exit 1
}

runtime_id="com.apple.CoreSimulator.SimRuntime.iOS-26-4"
xcrun simctl list runtimes | grep -Fq "$runtime_id (" || {
  printf 'required iOS 26.4 simulator runtime is unavailable\n' >&2
  exit 1
}

device_type_id="com.apple.CoreSimulator.SimDeviceType.iPhone-17-Pro"
xcrun simctl list devicetypes | grep -Fq "$device_type_id" || {
  printf 'required iPhone 17 Pro simulator type is unavailable\n' >&2
  exit 1
}

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

ios_app="$(find "$derived_data" -type d -path '*/Debug-iphonesimulator/SuperNutrition.app' -print -quit)"
test -n "$ios_app" || {
  printf 'iOS UI acceptance app was not produced\n' >&2
  exit 1
}
xcrun simctl install "$simulator_udid" "$ios_app"

if ! command -v maestro >/dev/null 2>&1; then
  maestro_archive="$temp_root/maestro.zip"
  curl --fail --location --silent --show-error \
    https://github.com/mobile-dev-inc/Maestro/releases/download/cli-2.7.0/maestro.zip \
    --output "$maestro_archive"
  expected_maestro_sha256="$(sed -n 's/^sha256=\([0-9a-f]\{64\}\)$/\1/p' "$repo_root/mobile/.maestro/maestro-cli-2.7.0.sha256")"
  test -n "$expected_maestro_sha256" || {
    printf 'missing Maestro checksum pin\n' >&2
    exit 1
  }
  if command -v sha256sum >/dev/null 2>&1; then
    printf '%s  %s\n' "$expected_maestro_sha256" "$maestro_archive" | sha256sum --check --status -
  else
    printf '%s  %s\n' "$expected_maestro_sha256" "$maestro_archive" | shasum --algorithm 256 --check --status -
  fi
  unzip -q "$maestro_archive" -d "$temp_root/maestro-dist"
  export PATH="$temp_root/maestro-dist/maestro/bin:$PATH"
fi
maestro_bin="$(command -v maestro || true)"
test -n "$maestro_bin" || {
  printf 'Maestro CLI is not available after installation\n' >&2
  exit 1
}
"$maestro_bin" --version | grep -Fq '2.7.0' || {
  printf 'Maestro CLI 2.7.0 is required\n' >&2
  exit 1
}
"$maestro_bin" check-syntax \
  "$repo_root/mobile/.maestro/photo-analysis/photo-analysis-capture-recovery.yaml"

"$maestro_bin" --device "$simulator_udid" test \
  "$repo_root/mobile/.maestro/photo-analysis" \
  --env APP_ID=com.supernutrition.ios.uiacceptance \
  --format junit \
  --output "$report_root/junit.xml" \
  --test-output-dir "$report_root/debug"
