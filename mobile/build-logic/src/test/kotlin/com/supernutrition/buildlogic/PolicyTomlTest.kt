package com.supernutrition.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PolicyTomlTest {
    @Test
    fun `shared parser extracts policy values and blocks consistently`() {
        val text = """
            lifecycle = "active"
            enabled = true
            count = 2
            fields = ["one", "two"]

            [[unit]]
            id = "first"

            [[unit]]
            id = "second"
        """.trimIndent()

        assertEquals("active", PolicyToml.string(text, "lifecycle"))
        assertEquals(true, PolicyToml.boolean(text, "enabled"))
        assertEquals(2, PolicyToml.integer(text, "count"))
        assertEquals(listOf("one", "two"), PolicyToml.stringList(text, "fields"))
        assertEquals(2, PolicyToml.blocks(text, "unit").size)
        assertTrue(PolicyToml.requiredManifests.contains("config/architecture/module-boundaries.toml"))
    }
}
