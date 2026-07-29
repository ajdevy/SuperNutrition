package com.supernutrition.platform.photoandroid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidPhotoBridgeContractTest {
    @Test
    fun `permission recovery always preserves import and manual search`() {
        val contract = CaptureSurfaceContract()

        assertEquals(
            listOf(
                PhotoRecoveryAction.OpenSettings,
                PhotoRecoveryAction.ImportPhoto,
                PhotoRecoveryAction.ManualSearch,
            ),
            contract.recoveryFor(CameraPermissionState.DENIED, CameraAvailability.AVAILABLE),
        )
    }

    @Test
    fun `contract exposes every permission and availability state`() {
        assertEquals(
            listOf(
                CameraPermissionState.NOT_DETERMINED,
                CameraPermissionState.GRANTED,
                CameraPermissionState.DENIED,
                CameraPermissionState.RESTRICTED,
            ),
            CameraPermissionState.entries,
        )
        assertEquals(
            listOf(CameraAvailability.AVAILABLE, CameraAvailability.UNAVAILABLE),
            CameraAvailability.entries,
        )
        assertTrue(CaptureSurfaceContract().cameraPermissionAutomationId.isNotBlank())
    }
}
