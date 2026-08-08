package com.supernutrition.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UiAutomationIdTest {
    @Test
    fun `camera capture contract exposes every permission and availability state`() {
        assertEquals(
            listOf(
                CameraPermissionState.NOT_DETERMINED,
                CameraPermissionState.GRANTED,
                CameraPermissionState.DENIED,
                CameraPermissionState.RATIONALE,
                CameraPermissionState.PERMANENTLY_DENIED,
                CameraPermissionState.RESTRICTED,
            ),
            CameraPermissionState.values().toList(),
        )
        assertEquals(
            listOf(CameraAvailability.AVAILABLE, CameraAvailability.UNAVAILABLE),
            CameraAvailability.values().toList(),
        )
    }

    @Test
    fun `registry is unique and locale neutral`() {
        val values = UiAutomationIds.all.map { it.value }

        assertEquals(values.size, values.toSet().size)
        assertTrue(values.all { it.matches(UI_AUTOMATION_ID_PATTERN) })
        assertEquals("bootstrap_shell_launch_di", UiAutomationIds.BOOTSTRAP_LAUNCH_DI.value)
        assertEquals("capture_shutter", UiAutomationIds.CAPTURE_SHUTTER.value)
        assertEquals("capture_import", UiAutomationIds.CAPTURE_IMPORT.value)
    }

    @Test
    fun `automation identifiers reject malformed values`() {
        listOf("", "UpperCase", "has-hyphen", "1starts-with-digit", "double__underscore").forEach {
            assertFailsWith<IllegalArgumentException> { UiAutomationId(it) }
        }
        assertEquals("photo_analysis_title", UiAutomationIds.PHOTO_ANALYSIS_TITLE.value)
        assertEquals("photo_analysis_grid_cell_9", photoAnalysisGridCellId(8).value)
        assertFailsWith<IllegalArgumentException> { photoAnalysisGridCellId(-1) }
        assertTrue(UiAutomationIds.all.contains(UiAutomationIds.CAPTURE_CANCEL))
    }

    @Test
    fun `visible component identifiers are stable and encode opaque punctuation`() {
        assertEquals(
            "photo_analysis_component_l69_l6e_l67_l72_l65_l64_l69_l65_l6e_l74_x3a_l72_l69_l63_l65",
            photoAnalysisVisibleComponentId("ingredient:rice").value,
        )
        assertNotEquals(
            photoAnalysisVisibleComponentId("A"),
            photoAnalysisVisibleComponentId("u41"),
        )
        assertTrue(photoAnalysisVisibleComponentId("component-rice").value.matches(UI_AUTOMATION_ID_PATTERN))
        assertTrue(photoAnalysisVisibleComponentId("!0AZz9-").value.matches(UI_AUTOMATION_ID_PATTERN))
        assertFailsWith<IllegalArgumentException> { photoAnalysisVisibleComponentId("") }
    }
}
