#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
ios_udid="${IOS_UDID:-}"
team_id="${IOS_DEVELOPMENT_TEAM:-${APPLE_TEAM_ID:-}}"
code_sign_identity="${IOS_CODE_SIGN_IDENTITY:-Apple Development}"
bundle_id="${IOS_FIXTURE_BUNDLE_ID:-}"
provisioning_profile="${IOS_PROVISIONING_PROFILE:-}"
results_root="${IOS_ARM64_TEST_RESULTS_ROOT:-$repo_root/mobile/platform/photo-ios/build/test-results/iosArm64Test}"
fixture_binary="$repo_root/mobile/platform/photo-ios/build/bin/iosArm64/debugTest/test.kexe"

for command_name in codesign plutil python3 security xcrun; do
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

# The Kotlin/Native test binary is wrapped in a temporary app, so it needs the
# same valid development profile as a real iPhone host. A certificate and a
# hand-written entitlements file are not sufficient for CoreDevice install.
# Prefer an explicitly supplied profile, then reuse the profile embedded in a
# locally built Debug-iphoneos acceptance app.
if test -z "$provisioning_profile" && test -n "${IOS_APP_PATH:-}" && \
  test -f "$IOS_APP_PATH/embedded.mobileprovision"; then
  provisioning_profile="$IOS_APP_PATH/embedded.mobileprovision"
fi
if test -z "$provisioning_profile"; then
  provisioning_profile="$(find "$derived_data_root" -type f \
    -path '*/Debug-iphoneos/*.app/embedded.mobileprovision' -print -quit)"
fi
test -f "$provisioning_profile" || {
  printf 'a valid iOS development provisioning profile is required; build the physical acceptance app first or set IOS_PROVISIONING_PROFILE\n' >&2
  exit 1
}

profile_plist="$runner_root/profile.plist"
security cms -D -i "$provisioning_profile" -o "$profile_plist" 2>/dev/null || {
  printf 'unable to decode iOS provisioning profile: %s\n' "$provisioning_profile" >&2
  exit 1
}
profile_app_id="$(plutil -extract Entitlements.application-identifier raw -o - "$profile_plist" 2>/dev/null || true)"
profile_team_id="$(plutil -extract TeamIdentifier.0 raw -o - "$profile_plist" 2>/dev/null || true)"
test -n "$profile_app_id" && test -n "$profile_team_id" || {
  printf 'provisioning profile lacks application identifier/team entitlements: %s\n' "$provisioning_profile" >&2
  exit 1
}
profile_bundle_pattern="${profile_app_id#*.}"
if test -z "$bundle_id"; then
  if test "$profile_bundle_pattern" = "*" || [[ "$profile_bundle_pattern" == *\** ]]; then
    printf 'IOS_FIXTURE_BUNDLE_ID is required for a wildcard provisioning profile\n' >&2
    exit 1
  fi
  bundle_id="$profile_bundle_pattern"
fi
if test "$profile_bundle_pattern" != "*" && [[ "$profile_bundle_pattern" != *\** ]] && \
  test "$bundle_id" != "$profile_bundle_pattern"; then
  printf 'fixture bundle id does not match provisioning profile: %s != %s\n' \
    "$bundle_id" "$profile_bundle_pattern" >&2
  exit 1
fi
if test -n "$team_id" && test "$team_id" != "$profile_team_id"; then
  printf 'requested development team does not match provisioning profile: %s != %s\n' \
    "$team_id" "$profile_team_id" >&2
  exit 1
fi
team_id="$profile_team_id"

mkdir -p "$app_path"
cp "$fixture_binary" "$app_path/SuperNutritionIosArm64FixtureRunner"
cp "$script_dir/ios-fixture-runner/Info.plist" "$app_path/Info.plist"
cp "$provisioning_profile" "$app_path/embedded.mobileprovision"
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
