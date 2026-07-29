package com.supernutrition.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class UiAutomationIdTest {
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
        assertTrue(UiAutomationIds.all.contains(UiAutomationIds.CAPTURE_CANCEL))
    }
}
