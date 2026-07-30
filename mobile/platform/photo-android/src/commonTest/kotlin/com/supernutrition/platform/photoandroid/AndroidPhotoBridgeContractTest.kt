package com.supernutrition.platform.photoandroid

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidPhotoBridgeContractTest {
    @Test
    fun `contract exposes every permission and availability state`() {
        assertEquals(
            listOf(
                CameraPermissionState.NOT_DETERMINED,
                CameraPermissionState.GRANTED,
                CameraPermissionState.DENIED,
                CameraPermissionState.RATIONALE,
                CameraPermissionState.PERMANENTLY_DENIED,
                CameraPermissionState.RESTRICTED,
            ),
            CameraPermissionState.entries,
        )
        assertEquals(
            listOf(CameraAvailability.AVAILABLE, CameraAvailability.UNAVAILABLE),
            CameraAvailability.entries,
        )
    }
}
