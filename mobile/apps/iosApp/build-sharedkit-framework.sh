#!/bin/sh
set -eu

case "${CONFIGURATION:-}" in
    Debug|Release) ;;
    *)
        printf 'error: Unsupported CONFIGURATION "%s"; expected Debug or Release.\n' "${CONFIGURATION:-}" >&2
        exit 1
        ;;
esac

case "${KOTLIN_TARGET:-}" in
    iosArm64|IosArm64)
        target_suffix=IosArm64
        enable_intel_x64=false
        ;;
    iosSimulatorArm64|IosSimulatorArm64)
        target_suffix=IosSimulatorArm64
        enable_intel_x64=false
        ;;
    iosX64|IosX64)
        target_suffix=IosX64
        enable_intel_x64=true
        ;;
    *)
        printf 'error: Unsupported KOTLIN_TARGET "%s"; expected iosArm64, iosSimulatorArm64, or iosX64.\n' "${KOTLIN_TARGET:-}" >&2
        exit 1
        ;;
esac

: "${PROJECT_DIR:?error: PROJECT_DIR must point to mobile/apps/iosApp}"
mobile_root=$(CDPATH= cd "${PROJECT_DIR}/../.." && pwd)
cd "${mobile_root}"

exec "${mobile_root}/gradlew" -p "${mobile_root}" \
    -Psupernutrition.enableSharedKitFramework=true \
    -Psupernutrition.enableIosDeviceTarget=true \
    -Psupernutrition.enableTestingAppleTargets=true \
    "-Psupernutrition.enableIntelIosX64=${enable_intel_x64}" \
    ":apps:sharedKit:link${CONFIGURATION}Framework${target_suffix}" \
    --dependency-verification=strict \
    --no-configuration-cache
