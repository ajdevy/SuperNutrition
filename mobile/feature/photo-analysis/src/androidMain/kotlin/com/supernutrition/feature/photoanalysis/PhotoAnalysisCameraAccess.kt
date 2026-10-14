package com.supernutrition.feature.photoanalysis

import com.supernutrition.core.ui.CameraPermissionState

internal data class PhotoAnalysisCameraAccess(
    val permissionState: CameraPermissionState,
    val requestInFlight: Boolean,
    val onAccessRequested: () -> Unit,
    val actionVisible: Boolean = true,
)
