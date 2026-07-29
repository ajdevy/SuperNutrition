package com.supernutrition.core.ui

enum class CameraPermissionState {
    NOT_DETERMINED,
    GRANTED,
    DENIED,
    RESTRICTED,
}

enum class CameraAvailability {
    AVAILABLE,
    UNAVAILABLE,
}

sealed interface PhotoRecoveryAction {
    data object OpenSettings : PhotoRecoveryAction
    data object ImportPhoto : PhotoRecoveryAction
    data object ManualSearch : PhotoRecoveryAction
}

data class CaptureSurfaceContract(
    val shutterAutomationId: String = UiAutomationIds.CAPTURE_SHUTTER.value,
    val importAutomationId: String = UiAutomationIds.CAPTURE_IMPORT.value,
    val cameraPermissionAutomationId: String = UiAutomationIds.CAPTURE_PERMISSION_RATIONALE.value,
) {
    fun recoveryFor(
        permission: CameraPermissionState,
        availability: CameraAvailability,
    ): List<PhotoRecoveryAction> = when {
        permission != CameraPermissionState.GRANTED -> listOf(
            PhotoRecoveryAction.OpenSettings,
            PhotoRecoveryAction.ImportPhoto,
            PhotoRecoveryAction.ManualSearch,
        )

        availability == CameraAvailability.UNAVAILABLE -> listOf(
            PhotoRecoveryAction.ImportPhoto,
            PhotoRecoveryAction.ManualSearch,
        )

        else -> emptyList()
    }
}
