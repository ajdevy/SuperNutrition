#!/bin/sh
set -eu

case "$#:${1:-}" in
    0:) sync_resources=false ;;
    1:--xcode-resources) sync_resources=true ;;
    *)
        printf 'error: Usage: build-sharedkit-framework.sh [--xcode-resources]\n' >&2
        exit 1
        ;;
esac

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

set -- ":apps:sharedKit:link${CONFIGURATION}Framework${target_suffix}"
if [ "${sync_resources}" = true ]; then
    # Compose's sync task uses these Xcode inputs to select transitive resources
    # and place them in the host bundle rather than the static framework.
    : "${BUILT_PRODUCTS_DIR:?error: BUILT_PRODUCTS_DIR is required for Xcode resource packaging}"
    : "${UNLOCALIZED_RESOURCES_FOLDER_PATH:?error: UNLOCALIZED_RESOURCES_FOLDER_PATH is required for Xcode resource packaging}"
    : "${PLATFORM_NAME:?error: PLATFORM_NAME is required for Xcode resource packaging}"
    : "${ARCHS:?error: ARCHS is required for Xcode resource packaging}"
    if [ "${target_suffix}" = IosX64 ]; then
        printf 'error: Compose 1.11.1 resource packaging does not support iosX64; use an arm64 iOS device or simulator.\n' >&2
        exit 1
    fi
    set -- "$@" :apps:sharedKit:syncComposeResourcesForIos
fi

: "${PROJECT_DIR:?error: PROJECT_DIR must point to mobile/apps/iosApp}"
mobile_root=$(CDPATH= cd "${PROJECT_DIR}/../.." && pwd)
cd "${mobile_root}"

exec "${mobile_root}/gradlew" -p "${mobile_root}" \
    -Psupernutrition.enableSharedKitFramework=true \
    -Psupernutrition.enableIosDeviceTarget=true \
    -Psupernutrition.enableTestingAppleTargets=true \
    "-Psupernutrition.enableIntelIosX64=${enable_intel_x64}" \
    "$@" \
    --dependency-verification=strict \
    --no-configuration-cache
