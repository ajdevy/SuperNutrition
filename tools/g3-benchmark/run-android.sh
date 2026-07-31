#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
: "${ANDROID_DEVICE_SERIAL:?Set ANDROID_DEVICE_SERIAL to one physical adb serial}"
EXPECTED_MODEL_SHA='9070f8097cda88923f77774d93d9fead7cee344486521c7fc5142806642061df'
EXPECTED_MEAL_MANIFEST_SHA='d312e9c7408aa474df38ff823d9fd3fc6c8a3a86cffc94df7ce53391c6975292'

"$ROOT_DIR/download-assets.sh"
adb -s "$ANDROID_DEVICE_SERIAL" get-state | grep -qx device
"$ROOT_DIR/../../mobile/gradlew" -p "$ROOT_DIR/android-runner" :app:assembleDebug \
  --no-daemon --no-configuration-cache
APK="$ROOT_DIR/android-runner/app/build/outputs/apk/debug/app-debug.apk"
RESULT_DIR="${G3_RUN_DIR:-$(mktemp -d /tmp/supernutrition-g3-android-run.XXXXXX)}"
mkdir -p "$RESULT_DIR"
rm -f "$RESULT_DIR/result.json" "$RESULT_DIR/result.json.tmp"
adb -s "$ANDROID_DEVICE_SERIAL" install -r "$APK" >/dev/null
adb -s "$ANDROID_DEVICE_SERIAL" logcat -c
adb -s "$ANDROID_DEVICE_SERIAL" shell run-as com.supernutrition.g3benchmark \
  rm -f files/result.json files/mobilenet_v2_food101.onnx
adb -s "$ANDROID_DEVICE_SERIAL" shell dumpsys battery > "$RESULT_DIR/battery-before.txt"
adb -s "$ANDROID_DEVICE_SERIAL" shell dumpsys thermalservice > "$RESULT_DIR/thermal-before.txt"
adb -s "$ANDROID_DEVICE_SERIAL" shell dumpsys meminfo com.supernutrition.g3benchmark > "$RESULT_DIR/meminfo-before.txt" || true
adb -s "$ANDROID_DEVICE_SERIAL" shell am force-stop com.supernutrition.g3benchmark
adb -s "$ANDROID_DEVICE_SERIAL" shell monkey -p com.supernutrition.g3benchmark 1 >/dev/null

for attempt in $(seq 1 "${G3_RESULT_POLL_ATTEMPTS:-180}"); do
  RESULT_TMP="$RESULT_DIR/result.json.tmp"
  if adb -s "$ANDROID_DEVICE_SERIAL" exec-out run-as \
      com.supernutrition.g3benchmark cat files/result.json > "$RESULT_TMP" 2>/dev/null \
      && jq -e --arg sha "$EXPECTED_MODEL_SHA" \
        --arg meal_sha "$EXPECTED_MEAL_MANIFEST_SHA" \
        '.status == "success" and .model_sha256 == $sha and .meal_benchmark_manifest_sha256 == $meal_sha and .meal_benchmark_fixture_count == 5 and (.ingredient_benchmark.status | type) == "string" and (.weight_benchmark.status | type) == "string"' "$RESULT_TMP" >/dev/null 2>&1; then
    mv "$RESULT_TMP" "$RESULT_DIR/result.json"
    break
  fi
  rm -f "$RESULT_TMP"
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
if ! jq -e '.status == "success" and .semantic_pass == true' "$RESULT_DIR/result.json" >/dev/null; then
  printf 'Android semantic acceptance failed.\n' >&2
  exit 2
fi
printf 'run_dir=%s\n' "$RESULT_DIR"
