#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
: "${IOS_DEVICE_UDID:?Set IOS_DEVICE_UDID to one physical device UDID}"
: "${DEVELOPMENT_TEAM:?Set DEVELOPMENT_TEAM to the Apple development team selected in Xcode}"

"$ROOT_DIR/download-assets.sh"
command -v xcodegen >/dev/null
command -v pod >/dev/null
xcodegen generate --spec "$ROOT_DIR/ios-runner/project.yml" --project "$ROOT_DIR/ios-runner"
(cd "$ROOT_DIR/ios-runner" && pod install)

WORKSPACE="$ROOT_DIR/ios-runner/SuperNutritionG3.xcworkspace"
DERIVED_DATA="$ROOT_DIR/ios-runner/build/DerivedData"
xcodebuild -workspace "$WORKSPACE" -scheme SuperNutritionG3 -configuration Debug \
  -destination "id=$IOS_DEVICE_UDID" -derivedDataPath "$DERIVED_DATA" \
  DEVELOPMENT_TEAM="$DEVELOPMENT_TEAM" CODE_SIGN_STYLE=Automatic \
  CODE_SIGN_IDENTITY='Apple Development' -allowProvisioningUpdates build >/dev/null

APP="$DERIVED_DATA/Build/Products/Debug-iphoneos/SuperNutritionG3.app"
xcrun devicectl device install app --device "$IOS_DEVICE_UDID" "$APP" --quiet
xcrun devicectl device process launch --device "$IOS_DEVICE_UDID" \
  --terminate-existing --no-activate com.supernutrition.g3 --quiet

RESULT_DIR="${G3_RUN_DIR:-$(mktemp -d /tmp/supernutrition-g3-ios.XXXXXX)}"
mkdir -p "$RESULT_DIR"
for attempt in $(seq 1 30); do
  if xcrun devicectl device copy from --device "$IOS_DEVICE_UDID" \
      --domain-type appDataContainer --domain-identifier com.supernutrition.g3 \
      --source Library/Caches/result.json --destination "$RESULT_DIR/result.json" \
      --quiet >/dev/null 2>&1; then
    cat "$RESULT_DIR/result.json"
    printf 'run_dir=%s\n' "$RESULT_DIR"
    exit 0
  fi
  sleep 1
done

printf 'iOS benchmark did not produce Library/Caches/result.json.\n' >&2
exit 1
