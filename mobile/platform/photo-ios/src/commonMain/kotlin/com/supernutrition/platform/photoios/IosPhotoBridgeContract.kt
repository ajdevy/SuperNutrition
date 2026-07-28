package com.supernutrition.platform.photoios

import com.supernutrition.core.ui.UiAutomationIds
enum class IosCameraPermissionState {
    NOT_DETERMINED,
    AUTHORIZED,
    DENIED,
    RESTRICTED,
}

data class IosCaptureSurfaceContract(
    val shutterAutomationId: String = UiAutomationIds.CAPTURE_SHUTTER.value,
    val importAutomationId: String = UiAutomationIds.CAPTURE_IMPORT.value,
    val permissionRationaleAutomationId: String = UiAutomationIds.CAPTURE_PERMISSION_RATIONALE.value,
)

interface IosPhotoBridgePort {
    fun cameraPermissionState(): IosCameraPermissionState
    fun cameraUsageDescription(): String
    fun photoPickerUsageDescription(): String
}
