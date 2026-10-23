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

# Full AOSP retains native platform UI without Google preload/background services.
system_image_tag=default
system_image_package="system-images;android-36;$system_image_tag;x86_64"
system_image_dir="$sdk_root/system-images/android-36/$system_image_tag/x86_64"

sdk_packages=()
test -x "$adb_bin" || sdk_packages+=("platform-tools")
test -x "$emulator_bin" || sdk_packages+=("emulator")
test -d "$sdk_root/platforms/android-36" || sdk_packages+=("platforms;android-36")
test -f "$system_image_dir/source.properties" ||
  sdk_packages+=("$system_image_package")
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

avd_name="supernutrition-api36-$system_image_tag-x86_64"
if ! test -f "$ANDROID_AVD_HOME/$avd_name.ini"; then
  printf 'no\n' | "$avdmanager_bin" create avd \
    --name "$avd_name" \
    --package "$system_image_package" \
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
# Preserve the original 1080x2400/420dpi viewport while rendering 55.56% fewer pixels.
display_width=720
display_height=1600
display_density=280
python3 - "$script_dir" "$avd_config" "$display_width" "$display_height" "$display_density" <<'DISPLAY'
import sys
sys.path.insert(0, sys.argv[1])
from android_boot_health import configure_avd_display
configure_avd_display(sys.argv[2], *(int(value) for value in sys.argv[3:]))
DISPLAY
printf 'Android AVD physical display: %sx%s at %sdpi\n' "$display_width" "$display_height" "$display_density"

data_partition_size="${SUPERNUTRITION_ANDROID_DATA_PARTITION_SIZE:-4096M}"
if grep -q '^disk\.dataPartition\.size=' "$avd_config"; then
  sed -i.bak "s/^disk\.dataPartition\.size=.*/disk.dataPartition.size=$data_partition_size/" "$avd_config"
else
  printf '\ndisk.dataPartition.size=%s\n' "$data_partition_size" >>"$avd_config"
fi
rm -f "$avd_config.bak"
printf 'Android AVD userdata size: '
grep '^disk\.dataPartition\.size=' "$avd_config"

cpu_cores="$(python3 - "${SUPERNUTRITION_ANDROID_CPU_CORES:-2}" <<'CPU'
import os
import sys
requested = int(sys.argv[1])
if requested < 1:
    raise SystemExit("Android CPU count must be positive")
available = len(os.sched_getaffinity(0)) if hasattr(os, "sched_getaffinity") else (os.cpu_count() or 1)
print(min(requested, available))
CPU
)"
if grep -q '^hw\.cpu\.ncore=' "$avd_config"; then
  sed -i.bak "s/^hw\.cpu\.ncore=.*/hw.cpu.ncore=$cpu_cores/" "$avd_config"
else
  printf 'hw.cpu.ncore=%s\n' "$cpu_cores" >>"$avd_config"
fi
rm -f "$avd_config.bak"
printf 'Android AVD CPU cores: '
grep '^hw\.cpu\.ncore=' "$avd_config"

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
  -skin "${display_width}x${display_height}" \
  -no-window \
  -no-audio \
  -no-boot-anim \
  -no-snapshot \
  "${emulator_accel_args[@]}" \
  -gpu swiftshader >"$temp_root/emulator.log" 2>&1 &
emulator_pid=$!
dump_ui_hierarchy() {
  test -n "${device_serial:-}" || return 0
  local remote_path=/sdcard/supernutrition-window.xml
  local local_path="$temp_root/ui-hierarchy.xml"
  python3 - "$adb_bin" "$device_serial" "$remote_path" "$local_path" <<'PY'
import subprocess
import sys
adb, serial, remote, local = sys.argv[1:]
try:
    subprocess.run([adb, "-s", serial, "shell", "uiautomator", "dump", remote],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10)
    subprocess.run([adb, "-s", serial, "pull", remote, local],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=5)
except (subprocess.SubprocessError, OSError):
    pass
PY
}
cleanup() {
  dump_ui_hierarchy
  if test -n "${logcat_pid:-}"; then
    kill "$logcat_pid" 2>/dev/null || true
    wait "$logcat_pid" 2>/dev/null || true
  fi
  kill "$emulator_pid" 2>/dev/null || true
}
trap cleanup EXIT

device_serial=""
logcat_path="$temp_root/logcat.log"
for _ in $(seq 1 "$boot_wait_iterations"); do
  if ! kill -0 "$emulator_pid" 2>/dev/null; then
    printf 'Android emulator exited before connecting\n' >&2
    tail -n 80 "$temp_root/emulator.log" >&2 || true
    exit 1
  fi
  device_serial="$("$adb_bin" devices | awk 'NR > 1 && $1 ~ /^emulator-/ && $2 == "device" {print $1; exit}')"
  if test -n "$device_serial"; then
    # Preserve startup chronology and system failures from the first connection.
    "$adb_bin" -s "$device_serial" logcat -b all -v threadtime >"$logcat_path" 2>&1 &
    logcat_pid=$!
    break
  fi
  sleep 2
done
test -n "$device_serial" || {
  printf 'Android emulator did not connect\n' >&2
  tail -n 80 "$temp_root/emulator.log" >&2 || true
  exit 1
}

service_wait_iterations="${SUPERNUTRITION_ANDROID_SERVICE_WAIT_ITERATIONS:-90}"
boot_health_timeout="${SUPERNUTRITION_ANDROID_BOOT_HEALTH_TIMEOUT_SECONDS:-$((boot_wait_iterations * 2 + service_wait_iterations * 2))}"
python3 "$script_dir/android_boot_health.py" \
  --adb "$adb_bin" \
  --serial "$device_serial" \
  --timeout-seconds "$boot_health_timeout" \
  --report "$report_root/startup-health"

for animation_scale in window_animation_scale transition_animation_scale animator_duration_scale; do
  "$adb_bin" -s "$device_serial" shell settings put global "$animation_scale" 0
done
printf 'Android native startup is healthy and UI animations are disabled\n'

export ANDROID_SERIAL="$device_serial"

"$repo_root/mobile/gradlew" -p "$repo_root/mobile" \
  :apps:androidApp:connectedUiAcceptanceDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.deviceSerial="$device_serial" \
  --dependency-verification=strict \
  --no-daemon \
  --no-configuration-cache \
  --max-workers=1

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
