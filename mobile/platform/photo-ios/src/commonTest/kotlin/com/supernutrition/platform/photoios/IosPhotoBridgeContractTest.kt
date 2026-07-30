package com.supernutrition.platform.photoios

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosPhotoBridgeContractTest {
    @Test
    fun `shared automation IDs stay equal across platform bridges`() {
        val contract = IosCaptureSurfaceContract()

        assertEquals("capture_shutter", contract.shutterAutomationId)
        assertEquals("capture_import", contract.importAutomationId)
        assertEquals("capture_permission_rationale", contract.cameraPermissionAutomationId)
    }

    @Test
    fun `permission state contract keeps every native outcome explicit`() {
        assertEquals(
            listOf(
                IosCameraPermissionState.NOT_DETERMINED,
                IosCameraPermissionState.GRANTED,
                IosCameraPermissionState.DENIED,
                IosCameraPermissionState.RATIONALE,
                IosCameraPermissionState.PERMANENTLY_DENIED,
                IosCameraPermissionState.RESTRICTED,
            ),
            IosCameraPermissionState.entries,
        )
        assertTrue(IosCaptureSurfaceContract().shutterAutomationId.isNotBlank())
    }
}
