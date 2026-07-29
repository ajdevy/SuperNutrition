package com.supernutrition.platform.photoandroid

import com.supernutrition.core.ui.CameraAvailability as SharedCameraAvailability
import com.supernutrition.core.ui.CameraPermissionState as SharedCameraPermissionState
import com.supernutrition.core.ui.CaptureSurfaceContract as SharedCaptureSurfaceContract
import com.supernutrition.core.ui.PhotoRecoveryAction as SharedPhotoRecoveryAction

typealias CameraAvailability = SharedCameraAvailability
typealias CameraPermissionState = SharedCameraPermissionState
typealias CaptureSurfaceContract = SharedCaptureSurfaceContract
typealias PhotoRecoveryAction = SharedPhotoRecoveryAction
typealias AndroidCameraPermissionState = SharedCameraPermissionState
typealias AndroidCaptureSurfaceContract = SharedCaptureSurfaceContract
typealias AndroidPhotoRecoveryAction = SharedPhotoRecoveryAction

interface PhotoBridgePort {
    fun cameraPermissionState(): CameraPermissionState
    fun cameraAvailability(): CameraAvailability
    fun recoveryFor(permission: CameraPermissionState, availability: CameraAvailability): List<PhotoRecoveryAction>
}
