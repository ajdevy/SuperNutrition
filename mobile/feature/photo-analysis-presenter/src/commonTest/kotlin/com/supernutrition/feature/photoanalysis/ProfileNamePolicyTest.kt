package com.supernutrition.feature.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileNamePolicyTest {
    @Test
    fun `optional names are trimmed while Unicode remains exact`() {
        assertEquals("", ProfileNamePolicy.normalize("  "))
        assertEquals("Анна 🥗", ProfileNamePolicy.normalize("  Анна 🥗  "))
        assertEquals("e\u0301lise", ProfileNamePolicy.normalize("e\u0301lise"))
    }

    @Test
    fun `limits reject long and control-bearing input without truncation`() {
        val maximum = "a".repeat(ProfileNamePolicy.MAX_LENGTH)
        assertEquals(maximum, ProfileNamePolicy.normalize(maximum))
        assertNull(ProfileNamePolicy.normalize(maximum + "b"))
        assertNull(ProfileNamePolicy.normalize("Alex\nOther"))
        assertNull(ProfileNamePolicy.normalize("Alex\u0000Other"))
    }

    @Test
    fun `blank deletes an existing name and unchanged valid names can still be saved`() {
        assertFalse(ProfileNamePolicy.canSubmit(" ", ""))
        assertTrue(ProfileNamePolicy.canSubmit(" ", "Alex"))
        assertTrue(ProfileNamePolicy.canSubmit("Alex", "Alex"))
        assertTrue(ProfileNamePolicy.canSubmit("Alex", ""))
        assertFalse(ProfileNamePolicy.canSubmit("A\nB", "Alex"))
    }
}
