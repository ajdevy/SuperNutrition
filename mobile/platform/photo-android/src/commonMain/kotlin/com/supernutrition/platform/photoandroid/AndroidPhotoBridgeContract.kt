package com.supernutrition.platform.photoandroid

import com.supernutrition.core.ui.CameraAvailability as SharedCameraAvailability
import com.supernutrition.core.ui.CameraPermissionState as SharedCameraPermissionState

typealias CameraAvailability = SharedCameraAvailability
typealias CameraPermissionState = SharedCameraPermissionState
typealias AndroidCameraPermissionState = SharedCameraPermissionState

interface PhotoBridgePort {
    fun cameraPermissionState(): CameraPermissionState
    fun cameraAvailability(): CameraAvailability
}
