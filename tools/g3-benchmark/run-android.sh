#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
: "${ANDROID_DEVICE_SERIAL:?Set ANDROID_DEVICE_SERIAL to one physical adb serial}"

"$ROOT_DIR/download-assets.sh"
adb -s "$ANDROID_DEVICE_SERIAL" get-state | grep -qx device
"$ROOT_DIR/../../mobile/gradlew" -p "$ROOT_DIR/android-runner" :app:assembleDebug \
  --no-daemon --no-configuration-cache
APK="$ROOT_DIR/android-runner/app/build/outputs/apk/debug/app-debug.apk"
RESULT_DIR="${G3_RUN_DIR:-$(mktemp -d /tmp/supernutrition-g3-android-run.XXXXXX)}"
mkdir -p "$RESULT_DIR"
adb -s "$ANDROID_DEVICE_SERIAL" install -r "$APK" >/dev/null
adb -s "$ANDROID_DEVICE_SERIAL" logcat -c
adb -s "$ANDROID_DEVICE_SERIAL" shell dumpsys battery > "$RESULT_DIR/battery-before.txt"
adb -s "$ANDROID_DEVICE_SERIAL" shell dumpsys thermalservice > "$RESULT_DIR/thermal-before.txt"
adb -s "$ANDROID_DEVICE_SERIAL" shell dumpsys meminfo com.supernutrition.g3benchmark > "$RESULT_DIR/meminfo-before.txt" || true
adb -s "$ANDROID_DEVICE_SERIAL" shell am force-stop com.supernutrition.g3benchmark
adb -s "$ANDROID_DEVICE_SERIAL" shell monkey -p com.supernutrition.g3benchmark 1 >/dev/null

for attempt in $(seq 1 30); do
  if adb -s "$ANDROID_DEVICE_SERIAL" exec-out run-as \
      com.supernutrition.g3benchmark cat files/result.json > "$RESULT_DIR/result.json" 2>/dev/null \
      && [[ -s "$RESULT_DIR/result.json" ]]; then
    break
  fi
  sleep 1
done

if [[ ! -s "$RESULT_DIR/result.json" ]]; then
  printf 'Android benchmark did not produce result.json.\n' >&2
  adb -s "$ANDROID_DEVICE_SERIAL" logcat -d > "$RESULT_DIR/logcat.txt"
  exit 1
fi
adb -s "$ANDROID_DEVICE_SERIAL" shell dumpsys battery > "$RESULT_DIR/battery-after.txt"
adb -s "$ANDROID_DEVICE_SERIAL" shell dumpsys thermalservice > "$RESULT_DIR/thermal-after.txt"
adb -s "$ANDROID_DEVICE_SERIAL" shell dumpsys meminfo com.supernutrition.g3benchmark > "$RESULT_DIR/meminfo-after.txt" || true
adb -s "$ANDROID_DEVICE_SERIAL" logcat -d -v time -s SuperNutritionG3:* > "$RESULT_DIR/logcat.txt"
adb -s "$ANDROID_DEVICE_SERIAL" shell getprop > "$RESULT_DIR/getprop.txt"
cat "$RESULT_DIR/result.json"
printf 'run_dir=%s\n' "$RESULT_DIR"
