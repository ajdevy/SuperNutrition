package com.supernutrition.sharedkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedKitBootstrapTest {
    @Test
    fun bootstrapIsIdempotent() {
        SharedKitBootstrap.resetForTests()
        assertTrue(SharedKitBootstrap.start())
        assertFalse(SharedKitBootstrap.start())
    }

    @Test
    fun automationRegistryProjectsTheBootstrapAndCaptureIds() {
        assertEquals("bootstrap_shell_launch_di", SharedKitAutomationIds.bootstrapLaunchMarker)
        assertEquals("photo_analysis_status", SharedKitAutomationIds.photoAnalysisStatus)
        assertEquals("capture_shutter", SharedKitAutomationIds.captureShutter)
        assertEquals("capture_import", SharedKitAutomationIds.captureImport)
        assertEquals("capture_native", SharedKitAutomationIds.captureNative)
    }
}
