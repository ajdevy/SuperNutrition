#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd "$script_dir/.." && pwd)"
temp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/supernutrition-ui-android"
report_root="$temp_root/report"
mkdir -p "$report_root/debug"

lifecycle="$(sed -n 's/^lifecycle = "\([^"]*\)"$/\1/p' "$repo_root/mobile/product-lifecycle.toml" | head -n 1)"
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

"$repo_root/mobile/gradlew" -p "$repo_root/mobile" \
  :apps:androidApp:assembleUiAcceptanceDebug \
  --dependency-verification=strict \
  --no-daemon \
  --no-configuration-cache
apk_path="$(find "$repo_root/mobile/apps/androidApp/build/outputs/apk" -type f -name '*uiAcceptance*debug.apk' -print -quit)"
test -n "$apk_path" || {
  printf 'uiAcceptance APK was not produced\n' >&2
  exit 1
}
"$adb_bin" -s "$device_serial" install -r "$apk_path" >/dev/null

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

"$maestro_bin" --device "$device_serial" test \
  "$repo_root/mobile/.maestro/photo-analysis" \
  --env APP_ID=com.supernutrition.android.uiAcceptance \
  --format junit \
  --output "$report_root/junit.xml" \
  --test-output-dir "$report_root/debug"
