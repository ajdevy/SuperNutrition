package com.supernutrition.platform.photoios

import com.supernutrition.core.ui.CameraPermissionState

typealias IosCameraPermissionState = CameraPermissionState

interface IosPhotoBridgePort {
    fun cameraPermissionState(): CameraPermissionState
    fun cameraUsageDescription(): String
    fun photoPickerUsageDescription(): String
}
