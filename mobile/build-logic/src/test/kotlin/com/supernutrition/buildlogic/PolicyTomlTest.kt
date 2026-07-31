package com.supernutrition.buildlogic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
        assertEquals(2, PolicyToml.blocks(PolicyToml.document(text), "unit").size)
        assertTrue(PolicyToml.requiredManifests.contains("config/architecture/module-boundaries.toml"))
    }

    @Test
    fun `typed document exposes tables without serializing policy text`() {
        val document = PolicyToml.document(
            """
                lifecycle = "active"

                [[unit]]
                id = "first"
            """.trimIndent(),
        )

        assertEquals("active", PolicyToml.string(document, "lifecycle"))
        assertEquals("first", PolicyToml.string(PolicyToml.blocks(document, "unit").single(), "id"))
    }

    @Test
    fun `forbidden import rules preserve source and configuration scopes`() {
        val rules = PolicyToml.forbiddenImportRules(
            """
                forbidden_imports = [
                    "android.* in mobile/domain/**",
                    "mobile.testing.* in distributable configurations",
                ]
            """.trimIndent(),
        )

        assertEquals("android.", rules[0].importPrefix)
        assertEquals("mobile/domain", rules[0].sourceScope)
        assertEquals("mobile.testing.", rules[1].importPrefix)
        assertEquals("distributable", rules[1].configurationScope)
    }

    @Test
    fun `typed parser handles comments escaped strings and trailing array commas`() {
        val text = """
            label = "meal #1" # comment outside the value
            values = [
                "one",
                "two",
            ]
        """.trimIndent()

        assertEquals("meal #1", PolicyToml.string(text, "label"))
        assertEquals(listOf("one", "two"), PolicyToml.stringList(text, "values"))
    }

    @Test
    fun `typed lookups preserve table fallback and reject duplicate policy declarations`() {
        val document = PolicyToml.document(
            """
                count = 7
                enabled = true
                fields = ["root"]

                [[allowed_edge]]
                forbidden_imports = ["android.* in mobile/domain/**"]
                count = 8
                enabled = false
                fields = ["table"]
            """.trimIndent(),
        )
        assertEquals(
            listOf("android.* in mobile/domain/**"),
            PolicyToml.stringList(document, "forbidden_imports"),
        )
        assertEquals("android.", PolicyToml.forbiddenImportRules(document).single().importPrefix)
        assertEquals(7, PolicyToml.integer(document, "count"))
        assertEquals(true, PolicyToml.boolean(document, "enabled"))
        assertEquals(listOf("root"), PolicyToml.stringList(document, "fields"))

        assertFailsWith<IllegalArgumentException> {
            PolicyToml.document("quality_claim = false\nquality_claim = true")
        }
        assertFailsWith<IllegalArgumentException> {
            PolicyToml.document("[activation]\nready = true\nready = false")
        }
        assertFailsWith<IllegalArgumentException> {
            PolicyToml.document("[activation]\nready = true\n\n[activation]\nready = false")
        }
    }

    @Test
    fun `cached typed documents cannot be mutated by consumers`() {
        val document = PolicyToml.document(
            """
                fields = ["one"]
                [[unit]]
                id = "first"
            """.trimIndent(),
        )

        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (document.root as MutableMap<String, PolicyToml.Value>)["fields"] = PolicyToml.Value.Text("tampered")
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (PolicyToml.blocks(document, "unit").single().values as MutableMap<String, PolicyToml.Value>)
                .clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (document.tables as MutableList<PolicyToml.Table>).clear()
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            ((document.root["fields"] as PolicyToml.Value.ListValue).values as MutableList<PolicyToml.Value>)
                .clear()
        }
        assertEquals(listOf("one"), PolicyToml.stringList(document, "fields"))
    }
}
