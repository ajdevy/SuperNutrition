package com.supernutrition.platform.photoandroid

import com.supernutrition.core.ui.UiAutomationIds
enum class CameraPermissionState {
    UNKNOWN,
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
    val recoveryActions: List<PhotoRecoveryAction> = listOf(
        PhotoRecoveryAction.OpenSettings,
        PhotoRecoveryAction.ImportPhoto,
        PhotoRecoveryAction.ManualSearch,
    ),
)

interface PhotoBridgePort {
    fun cameraPermissionState(): CameraPermissionState
    fun cameraAvailability(): CameraAvailability
    fun recoveryFor(permission: CameraPermissionState, availability: CameraAvailability): List<PhotoRecoveryAction>
}
