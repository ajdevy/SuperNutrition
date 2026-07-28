#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
ios_udid="${IOS_UDID:-}"
team_id="${IOS_DEVELOPMENT_TEAM:-${APPLE_TEAM_ID:-}}"
code_sign_identity="${IOS_CODE_SIGN_IDENTITY:-Apple Development}"
bundle_id="${IOS_FIXTURE_BUNDLE_ID:-com.supernutrition.ios.arm64fixture.runner}"
results_root="${IOS_ARM64_TEST_RESULTS_ROOT:-$repo_root/mobile/platform/photo-ios/build/test-results/iosArm64Test}"
fixture_binary="$repo_root/mobile/platform/photo-ios/build/bin/iosArm64/debugTest/test.kexe"

for command_name in codesign plutil python3 xcrun; do
  command -v "$command_name" >/dev/null 2>&1 || {
    printf 'missing required command: %s\n' "$command_name" >&2
    exit 1
  }
done
test -x "$repo_root/mobile/gradlew" || {
  printf 'missing mobile Gradle wrapper: %s\n' "$repo_root/mobile/gradlew" >&2
  exit 1
}
test -n "$ios_udid" || {
  printf 'IOS_UDID must name a physical iPhone\n' >&2
  exit 2
}
test -n "$team_id" || {
  printf 'IOS_DEVELOPMENT_TEAM or APPLE_TEAM_ID is required\n' >&2
  exit 2
}

device_line="$(xcrun xctrace list devices 2>/dev/null | awk -v udid="$ios_udid" '
  /^== Devices ==$/ { online = 1; next }
  /^== / { online = 0 }
  online && index($0, udid) { print; exit }
' || true)"
test -n "$device_line" || {
  printf 'UDID is not an attached physical iOS device: %s\n' "$ios_udid" >&2
  exit 1
}

"$repo_root/mobile/gradlew" -p "$repo_root/mobile" :platform:photo-ios:iosArm64TestBinaries \
  -Psupernutrition.hostSlice=sharedKit \
  -Psupernutrition.enableIosDeviceTarget=true \
  --dependency-verification=strict --no-configuration-cache
test -x "$fixture_binary" || {
  printf 'iosArm64 fixture binary is missing: %s\n' "$fixture_binary" >&2
  exit 1
}

mkdir -p "$results_root"
rm -f "$results_root"/*IosImageNormalizerFixtureTest.xml \
  "$results_root"/*IosPhotoBridgeContractTest.xml \
  "$results_root/iosArm64FixtureRunner.log"
runner_root="$(mktemp -d "${TMPDIR:-/tmp}/supernutrition-ios-fixture.XXXXXX")"
trap 'rm -rf "$runner_root"' EXIT
app_path="$runner_root/iosArm64FixtureRunner.app"
mkdir -p "$app_path"
cp "$fixture_binary" "$app_path/SuperNutritionIosArm64FixtureRunner"
cp "$script_dir/ios-fixture-runner/Info.plist" "$app_path/Info.plist"
cp "$script_dir/ios-fixture-runner/Entitlements.plist" "$runner_root/Entitlements.plist"
plutil -replace CFBundleIdentifier -string "$bundle_id" "$app_path/Info.plist"
/usr/libexec/PlistBuddy -c "Set :application-identifier $team_id.$bundle_id" \
  -c "Set :com.apple.developer.team-identifier $team_id" "$runner_root/Entitlements.plist"

codesign --force --sign "$code_sign_identity" --timestamp=none \
  "$app_path/SuperNutritionIosArm64FixtureRunner"
codesign --force --sign "$code_sign_identity" \
  --entitlements "$runner_root/Entitlements.plist" --timestamp=none "$app_path"
codesign --verify --deep --strict "$app_path"

xcrun devicectl device install app --device "$ios_udid" "$app_path" --quiet
raw_log="$results_root/iosArm64FixtureRunner.log"
set +e
xcrun devicectl device process launch --device "$ios_udid" --console \
  --terminate-existing "$bundle_id" --ktest_logger=teamcity 2>&1 | tee "$raw_log"
launch_status=${PIPESTATUS[0]}
set -e

python3 "$script_dir/parse-ios-fixture-teamcity.py" "$raw_log" "$results_root"
if test "$launch_status" -ne 0; then
  printf 'physical iOS fixture runner exited with status %s\n' "$launch_status" >&2
  exit "$launch_status"
fi

printf 'physical iOS fixtures: 5 passed (3 normalization, 2 bridge), device=%s\n' "$device_line"
printf 'JUnit results: %s\n' "$results_root"
