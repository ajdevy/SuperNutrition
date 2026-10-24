package com.supernutrition.core.config

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrivateDocumentEncodingTest {
    @Test
    fun `strict UTF8 preserves empty unicode and delimiters`() {
        listOf("", "Alex", "Анна 🥗\nSNMD1:0:").forEach { value ->
            assertEquals(value, PrivateDocumentEncoding.decode(PrivateDocumentEncoding.encode(value)))
        }
        assertContentEquals(byteArrayOf(), PrivateDocumentEncoding.encode(""))
    }

    @Test
    fun `malformed UTF8 and unpaired surrogates never become substituted identity`() {
        assertFailsWith<CharacterCodingException> { PrivateDocumentEncoding.decode(byteArrayOf(0xC3.toByte(), 0x28)) }
        assertFailsWith<CharacterCodingException> { PrivateDocumentEncoding.encode("\uD800") }
    }

    @Test
    fun `oversize reads and writes fail before being accepted`() {
        assertFailsWith<IllegalArgumentException> {
            PrivateDocumentEncoding.decode(ByteArray(PrivateDocumentEncoding.MAX_BYTES + 1))
        }
        assertFailsWith<IllegalArgumentException> {
            PrivateDocumentEncoding.encode("a".repeat(PrivateDocumentEncoding.MAX_BYTES + 1))
        }
    }

    @Test
    fun `memory stores are isolated unless explicitly shared`() {
        val store = InMemoryPrivateDocumentStore()
        assertNull(store.load())
        assertTrue(store.save("saved"))
        assertEquals("saved", store.load())
        assertNull(InMemoryPrivateDocumentStore().load())
        assertEquals("initial", InMemoryPrivateDocumentStore("initial").load())
        assertEquals(setOf("meal-diary.txt", "profile-name.txt"), PrivateDocumentId.entries.map { it.fileName }.toSet())
    }
}
