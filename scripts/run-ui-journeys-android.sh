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

android_user_home="${ANDROID_USER_HOME:-$HOME/.android}"
export ANDROID_USER_HOME="$android_user_home"
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-$android_user_home/avd}"
mkdir -p "$ANDROID_AVD_HOME"

sdkmanager_bin="${ANDROID_SDKMANAGER:-$sdk_root/cmdline-tools/latest/bin/sdkmanager}"
avdmanager_bin="${ANDROID_AVDMANAGER:-$sdk_root/cmdline-tools/latest/bin/avdmanager}"
emulator_bin="${ANDROID_EMULATOR:-$sdk_root/emulator/emulator}"
adb_bin="${ANDROID_ADB:-$sdk_root/platform-tools/adb}"
for executable in "$sdkmanager_bin" "$avdmanager_bin"; do
  test -x "$executable" || {
    printf 'missing Android tool: %s\n' "$executable" >&2
    exit 1
  }
done

sdk_packages=()
test -x "$adb_bin" || sdk_packages+=("platform-tools")
test -x "$emulator_bin" || sdk_packages+=("emulator")
test -d "$sdk_root/platforms/android-36" || sdk_packages+=("platforms;android-36")
test -f "$sdk_root/system-images/android-36/google_apis/x86_64/source.properties" ||
  sdk_packages+=("system-images;android-36;google_apis;x86_64")
if (("${#sdk_packages[@]}" > 0)); then
  sdk_install_timeout="${SUPERNUTRITION_SDK_INSTALL_TIMEOUT_SECONDS:-600}"
  python3 - "$sdkmanager_bin" "$sdk_install_timeout" "${sdk_packages[@]}" <<'PY'
import subprocess
import sys

sdkmanager, timeout, *packages = sys.argv[1:]
timeout_seconds = float(timeout)
try:
    subprocess.run(
        [sdkmanager, "--licenses"],
        check=True,
        input=("y\n" * 64).encode(),
        stdout=subprocess.DEVNULL,
        timeout=timeout_seconds,
    )
    subprocess.run(
        [sdkmanager, "--install", *packages],
        check=True,
        timeout=timeout_seconds,
    )
except subprocess.TimeoutExpired:
    print(
        f"Android SDK installation exceeded {timeout_seconds:g} seconds",
        file=sys.stderr,
    )
    raise SystemExit(124)
PY
fi

for cache_dir in "$sdk_root/.temp" "$sdk_root/.cache" "$ANDROID_USER_HOME/cache"; do
  if test -d "$cache_dir"; then
    find "$cache_dir" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
  fi
done
if test "${GITHUB_ACTIONS:-}" = "true"; then
  for image_dir in "$sdk_root"/system-images/android-*; do
    test -d "$image_dir" || continue
    test "$image_dir" = "$sdk_root/system-images/android-36" && continue
    rm -rf -- "$image_dir"
  done

  for platform_dir in "$sdk_root"/platforms/android-*; do
    test -d "$platform_dir" || continue
    test "$platform_dir" = "$sdk_root/platforms/android-36" && continue
    rm -rf -- "$platform_dir"
  done
  for disposable_sdk_dir in "$sdk_root/sources" "$sdk_root/ndk" "$sdk_root/cmake" "$sdk_root/extras"; do
    test -d "$disposable_sdk_dir" || continue
    rm -rf -- "$disposable_sdk_dir"
  done
  for disposable_host_dir in /usr/share/dotnet /opt/ghc; do
    test -d "$disposable_host_dir" || continue
    sudo rm -rf -- "$disposable_host_dir"
  done

  gradle_user_home="${GRADLE_USER_HOME:-$HOME/.gradle}"
  if test -d "$gradle_user_home/caches"; then
    printf 'Removing restored Gradle caches before Android emulator:\n' >&2
    du -sh "$gradle_user_home/caches" >&2 || true
    find "$gradle_user_home/caches" -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +
  fi
fi
df -h "$ANDROID_AVD_HOME" >&2 || true

for executable in "$emulator_bin" "$adb_bin"; do
  test -x "$executable" || {
    printf 'missing Android tool after SDK setup: %s\n' "$executable" >&2
    exit 1
  }
done

# Build before starting the emulator so the guest does not sit idle while
# Gradle downloads dependencies and compiles the APKs.
"$repo_root/mobile/gradlew" -p "$repo_root/mobile" \
  :apps:androidApp:assembleUiAcceptanceDebug \
  :apps:androidApp:assembleUiAcceptanceDebugAndroidTest \
  --dependency-verification=strict \
  --no-daemon \
  --no-configuration-cache

avd_name="supernutrition-api36-x86_64"
if ! test -f "$ANDROID_AVD_HOME/$avd_name.ini"; then
  printf 'no\n' | "$avdmanager_bin" create avd \
    --name "$avd_name" \
    --package "system-images;android-36;google_apis;x86_64" \
    --device "pixel_6" \
    --force
fi
test -f "$ANDROID_AVD_HOME/$avd_name.ini" || {
  printf 'Android AVD was not created at %s\n' "$ANDROID_AVD_HOME/$avd_name.ini" >&2
  "$avdmanager_bin" list avd >&2 || true
  exit 1
}

avd_config="$ANDROID_AVD_HOME/$avd_name.avd/config.ini"
test -f "$avd_config" || {
  printf 'Android AVD config was not created at %s\n' "$avd_config" >&2
  exit 1
}
data_partition_size="${SUPERNUTRITION_ANDROID_DATA_PARTITION_SIZE:-4096M}"
if grep -q '^disk\.dataPartition\.size=' "$avd_config"; then
  sed -i.bak "s/^disk\.dataPartition\.size=.*/disk.dataPartition.size=$data_partition_size/" "$avd_config"
else
  printf '\ndisk.dataPartition.size=%s\n' "$data_partition_size" >>"$avd_config"
fi
rm -f "$avd_config.bak"
printf 'Android AVD userdata size: '
grep '^disk\.dataPartition\.size=' "$avd_config"

emulator_accel_args=()
if ! test -r /dev/kvm || ! test -w /dev/kvm; then
  printf 'KVM is unavailable; running the Android emulator without VM acceleration\n' >&2
  emulator_accel_args=(-no-accel)
fi
boot_wait_iterations="${SUPERNUTRITION_ANDROID_BOOT_WAIT_ITERATIONS:-90}"
if ((${#emulator_accel_args[@]} > 0)); then
  boot_wait_iterations="${SUPERNUTRITION_ANDROID_SOFTWARE_BOOT_WAIT_ITERATIONS:-180}"
fi

"$adb_bin" start-server >/dev/null
"$emulator_bin" -avd "$avd_name" \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -no-snapshot \
  "${emulator_accel_args[@]}" \
  -gpu swiftshader >"$temp_root/emulator.log" 2>&1 &
emulator_pid=$!
cleanup() {
  kill "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT

device_serial=""
for _ in $(seq 1 "$boot_wait_iterations"); do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then
    printf 'Android emulator exited before boot completed\n' >&2
    tail -n 80 "$temp_root/emulator.log" >&2 || true
    exit 1
  fi
  device_serial="$("$adb_bin" devices | awk 'NR > 1 && $1 ~ /^emulator-/ && $2 == "device" {print $1; exit}')"
  if test -n "$device_serial"; then
    boot_completed="$("$adb_bin" -s "$device_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
    if test "$boot_completed" = "1"; then
      break
    fi
  fi
  sleep 2
done
test -n "$device_serial" &&
  test "$("$adb_bin" -s "$device_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" || {
  printf 'Android emulator did not finish booting\n' >&2
  tail -n 80 "$temp_root/emulator.log" >&2 || true
  exit 1
}

service_wait_iterations="${SUPERNUTRITION_ANDROID_SERVICE_WAIT_ITERATIONS:-90}"
services_ready=false
for _ in $(seq 1 "$service_wait_iterations"); do
  activity_service="$("$adb_bin" -s "$device_serial" shell service check activity 2>/dev/null || true)"
  package_service="$("$adb_bin" -s "$device_serial" shell service check package 2>/dev/null || true)"
  if [[ "$activity_service" == *"found"* && "$package_service" == *"found"* ]]; then
    services_ready=true
    break
  fi
  sleep 2
done
if test "$services_ready" != true; then
  printf 'Android framework services did not become ready after boot\n' >&2
  printf 'activity: %s\npackage: %s\n' "$activity_service" "$package_service" >&2
  tail -n 80 "$temp_root/emulator.log" >&2 || true
  exit 1
fi
printf 'Android framework services are ready: activity and package\n'

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
  --env APP_ID=app.supernutrition.uiAcceptance \
  --format junit \
  --output "$report_root/junit.xml" \
  --test-output-dir "$report_root/debug"
python3 "$repo_root/scripts/verify_ui_test_reports.py" \
  --junit-root "$report_root" \
  --label "Android Maestro"
