#!/usr/bin/env bash
set -euo pipefail

configuration="${1:?configuration is required}"
kotlin_target="${2:?KOTLIN_TARGET is required}"
enable_intel_x64=false

case "${kotlin_target}" in
  iosArm64) gradle_target="IosArm64" ;;
  iosSimulatorArm64) gradle_target="IosSimulatorArm64" ;;
  iosX64)
    gradle_target="IosX64"
    enable_intel_x64=true
    ;;
  *) echo "Unsupported KOTLIN_TARGET: ${kotlin_target}" >&2; exit 1 ;;
esac

exec ./mobile/gradlew -p mobile \
  -Psupernutrition.enableSharedKitFramework=true \
  -Psupernutrition.hostSlice=sharedKit \
  -Psupernutrition.enableIosDeviceTarget=true \
  -Psupernutrition.enableTestingAppleTargets=true \
  -Psupernutrition.enableIntelIosX64="${enable_intel_x64}" \
  ":apps:sharedKit:link${configuration}Framework${gradle_target}"
