#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
source "$script_dir/lib/quality-common.sh"
temp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/supernutrition-ui-android"
report_root="$temp_root/report"
mkdir -p "$report_root/debug"

lifecycle="$(lifecycle_value "$repo_root" mobile/product-lifecycle.toml)"
printf 'lifecycle=%s\n' "$lifecycle" >"$report_root/lifecycle.txt"
if test "$lifecycle" != "active"; then
  exec "$repo_root/mobile/gradlew" -p "$repo_root/mobile" \
    qualityCheckJvmAndroid \
    --dependency-verification=strict \
    --no-daemon \
    --no-configuration-cache
fi

sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
test -n "$sdk_root" || {
  printf 'Android SDK root is not configured\n' >&2
  exit 1
}

sdkmanager_bin="${ANDROID_SDKMANAGER:-$sdk_root/cmdline-tools/latest/bin/sdkmanager}"
avdmanager_bin="${ANDROID_AVDMANAGER:-$sdk_root/cmdline-tools/latest/bin/avdmanager}"
emulator_bin="${ANDROID_EMULATOR:-$sdk_root/emulator/emulator}"
adb_bin="${ANDROID_ADB:-$sdk_root/platform-tools/adb}"
for executable in "$sdkmanager_bin" "$avdmanager_bin" "$emulator_bin" "$adb_bin"; do
  test -x "$executable" || {
    printf 'missing Android tool: %s\n' "$executable" >&2
    exit 1
  }
done

"$sdkmanager_bin" --install \
  "platform-tools" \
  "emulator" \
  "platforms;android-36" \
  "system-images;android-36;google_apis;x86_64"
yes | "$sdkmanager_bin" --licenses >/dev/null

avd_name="supernutrition-api36-x86_64"
if ! "$avdmanager_bin" list avd | grep -Fq "Name: $avd_name"; then
  printf 'no\n' | "$avdmanager_bin" create avd \
    --name "$avd_name" \
    --package "system-images;android-36;google_apis;x86_64" \
    --device "pixel_6"
fi

"$emulator_bin" -avd "$avd_name" \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -no-snapshot \
  -gpu swiftshader_indirect >"$temp_root/emulator.log" 2>&1 &
emulator_pid=$!
cleanup() {
  kill "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT

"$adb_bin" start-server >/dev/null
"$adb_bin" wait-for-device
for _ in $(seq 1 90); do
  boot_completed="$("$adb_bin" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
  if test "$boot_completed" = "1"; then
    break
  fi
  sleep 2
done
test "$("$adb_bin" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" || {
  printf 'Android emulator did not finish booting\n' >&2
  exit 1
}

device_serial="$("$adb_bin" devices | awk 'NR > 1 && $2 == "device" {print $1; exit}')"
test -n "$device_serial" || {
  printf 'no online Android device was discovered\n' >&2
  exit 1
}
export ANDROID_SERIAL="$device_serial"

"$repo_root/mobile/gradlew" -p "$repo_root/mobile" \
  :apps:androidApp:connectedUiAcceptanceDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.deviceSerial="$device_serial" \
  --dependency-verification=strict \
  --no-daemon \
  --no-configuration-cache

find "$repo_root/mobile/apps/androidApp/build" -type f \
  \( -path '*/androidTest-results/*' -o -path '*/reports/androidTests/*' \) \
  -print >"$report_root/android-instrumentation-files.txt"
python3 "$repo_root/scripts/verify_ui_test_reports.py" \
  --junit-root "$repo_root/mobile/apps/androidApp/build/outputs/androidTest-results" \
  --label "Android instrumentation"

"$repo_root/mobile/gradlew" -p "$repo_root/mobile" \
  :apps:androidApp:assembleUiAcceptanceDebug \
  --dependency-verification=strict \
  --no-daemon \
  --no-configuration-cache
apk_path="$(find "$repo_root/mobile/apps/androidApp/build/outputs/apk" -type f \
  -name '*uiAcceptance*debug.apk' ! -name '*androidTest*' -print -quit)"
test -n "$apk_path" || {
  printf 'uiAcceptance APK was not produced\n' >&2
  exit 1
}
"$adb_bin" -s "$device_serial" install -r "$apk_path" >/dev/null

maestro_bin="$(ensure_maestro_cli "$repo_root" "$temp_root")"
"$maestro_bin" check-syntax \
  "$repo_root/mobile/.maestro/photo-analysis/photo-analysis-capture-recovery.yaml"

"$maestro_bin" --device "$device_serial" test \
  "$repo_root/mobile/.maestro/photo-analysis" \
  --env APP_ID=com.supernutrition.android.uiAcceptance \
  --format junit \
  --output "$report_root/junit.xml" \
  --test-output-dir "$report_root/debug"
python3 "$repo_root/scripts/verify_ui_test_reports.py" \
  --junit-root "$report_root" \
  --label "Android Maestro"
