#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
: "${ANDROID_DEVICE_SERIAL:?Set ANDROID_DEVICE_SERIAL to one physical adb serial}"

"$ROOT_DIR/download-assets.sh"
adb -s "$ANDROID_DEVICE_SERIAL" get-state | grep -qx device
"$ROOT_DIR/../../mobile/gradlew" -p "$ROOT_DIR/android-runner" :app:assembleDebug \
  --no-daemon --no-configuration-cache

APK="$ROOT_DIR/android-runner/app/build/outputs/apk/debug/app-debug.apk"
adb -s "$ANDROID_DEVICE_SERIAL" install -r "$APK" >/dev/null
adb -s "$ANDROID_DEVICE_SERIAL" shell am force-stop com.supernutrition.g3benchmark
adb -s "$ANDROID_DEVICE_SERIAL" shell am start -W \
  -n com.supernutrition.g3benchmark/.PocActivity

printf 'PocActivity launched on %s\n' "$ANDROID_DEVICE_SERIAL"
