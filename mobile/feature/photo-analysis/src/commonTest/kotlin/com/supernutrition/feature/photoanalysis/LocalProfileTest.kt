package com.supernutrition.feature.photoanalysis

import com.supernutrition.core.config.InMemoryPrivateDocumentStore
import com.supernutrition.core.config.PrivateDocumentStore
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalProfileTest {
    @Test
    fun `name saves and explicit clearing survive a fresh instance`() {
        val store = InMemoryPrivateDocumentStore()
        val profile = LocalProfile(store)
        assertEquals("", profile.displayName.value)
        assertFalse(profile.loadFailed)
        assertTrue(profile.updateDisplayName("  Анна 🥗  "))
        assertEquals("Анна 🥗", LocalProfile(store).displayName.value)
        assertTrue(profile.updateDisplayName("  "))
        assertEquals("", LocalProfile(store).displayName.value)
        assertEquals("SNPN1:", store.load())
        assertNull(profile.failure.value)
    }

    @Test
    fun `a failed save or deletion leaves the last durable name and supports retry`() {
        val store = FailingProfileStore()
        val profile = LocalProfile(store)
        assertTrue(profile.updateDisplayName("Alex"))
        store.canSave = false
        assertFalse(profile.updateDisplayName("Other"))
        assertFalse(profile.updateDisplayName(""))
        assertEquals("Alex", profile.displayName.value)
        assertEquals("Alex", LocalProfile(store).displayName.value)
        assertEquals(LocalProfileFailure.WRITE_FAILED, profile.failure.value)
        store.canSave = true
        assertTrue(profile.updateDisplayName(""))
        assertNull(profile.failure.value)
    }

    @Test
    fun `corrupt unsupported and noncanonical stored names are preserved and block writes`() {
        listOf("SNPN2:Alex", "Alex", "SNPN1: Alex ", "SNPN1:A\nB", "SNPN1:" + "a".repeat(201)).forEach { encoded ->
            val store = InMemoryPrivateDocumentStore(encoded)
            val profile = LocalProfile(store)
            assertEquals("", profile.displayName.value)
            assertTrue(profile.loadFailed)
            assertEquals(LocalProfileFailure.CORRUPT_STORAGE, profile.failure.value)
            assertFalse(profile.updateDisplayName("New"))
            assertEquals(encoded, store.load())
        }
    }

    @Test
    fun `invalid user input never replaces a valid name`() {
        val store = InMemoryPrivateDocumentStore("SNPN1:Alex")
        val profile = LocalProfile(store)
        assertFalse(profile.updateDisplayName("a".repeat(201)))
        assertFalse(profile.updateDisplayName("A\nB"))
        assertEquals("Alex", profile.displayName.value)
        assertEquals("SNPN1:Alex", store.load())
    }

    @Test
    fun `read and write exceptions expose typed failures without exposing private paths`() {
        val unreadable = object : PrivateDocumentStore {
            override fun load(): String? = error("private path")
            override fun save(encoded: String) = true
        }
        val failedRead = LocalProfile(unreadable)
        assertTrue(failedRead.loadFailed)
        assertEquals(LocalProfileFailure.LOAD_FAILED, failedRead.failure.value)
        assertFalse(failedRead.updateDisplayName("Alex"))
        val failedWrite = LocalProfile(object : PrivateDocumentStore {
            override fun load(): String? = null
            override fun save(encoded: String): Boolean = error("private path")
        })
        assertFalse(failedWrite.updateDisplayName("Alex"))
        assertEquals(LocalProfileFailure.WRITE_FAILED, failedWrite.failure.value)
    }

    @Test
    fun `cancellation propagates through reads and writes`() {
        assertFailsWith<CancellationException> {
            LocalProfile(object : PrivateDocumentStore {
                override fun load(): String? = throw CancellationException()
                override fun save(encoded: String) = true
            })
        }
        val profile = LocalProfile(object : PrivateDocumentStore {
            override fun load(): String? = null
            override fun save(encoded: String): Boolean = throw CancellationException()
        })
        assertFailsWith<CancellationException> { profile.updateDisplayName("Alex") }
        assertEquals("", profile.displayName.value)
        assertNull(profile.failure.value)
    }
}

private class FailingProfileStore : PrivateDocumentStore {
    var canSave = true
    private var encoded: String? = null
    override fun load(): String? = encoded
    override fun save(encoded: String): Boolean {
        if (canSave) this.encoded = encoded
        return canSave
    }
}
