package com.supernutrition.platform.photoios

import com.supernutrition.core.ui.CameraPermissionState
import com.supernutrition.core.ui.CaptureSurfaceContract

typealias IosCameraPermissionState = CameraPermissionState
typealias IosCaptureSurfaceContract = CaptureSurfaceContract

interface IosPhotoBridgePort {
    fun cameraPermissionState(): CameraPermissionState
    fun cameraUsageDescription(): String
    fun photoPickerUsageDescription(): String
}
