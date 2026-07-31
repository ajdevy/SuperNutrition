package com.supernutrition.platform.photoios

import com.supernutrition.core.ui.UiAutomationIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IosPhotoBridgeContractTest {
    @Test
    fun `shared automation IDs stay equal across platform bridges`() {
        assertEquals("capture_shutter", UiAutomationIds.CAPTURE_SHUTTER.value)
        assertEquals("capture_import", UiAutomationIds.CAPTURE_IMPORT.value)
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
        assertTrue(UiAutomationIds.CAPTURE_SHUTTER.value.isNotBlank())
    }
}
