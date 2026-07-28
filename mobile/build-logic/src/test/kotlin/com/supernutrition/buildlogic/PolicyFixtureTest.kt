package com.supernutrition.buildlogic

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolicyFixtureTest {
    @Test
    fun `policy fixture rejects forbidden project repository markers`() {
        val fixture = "repositories { maven { url = uri(\"../another-product\") } }"

        assertTrue(fixture.contains("another-product"))
        assertFalse(fixture.contains("mavenCentral()"))
    }

    @Test
    fun `policy fixture accepts immutable catalog versions`() {
        val version = "2.4.10"

        assertTrue(version.matches(Regex("\\d+\\.\\d+\\.\\d+")))
    }
}
