package com.supernutrition.platform.photoandroid

import java.util.LinkedHashMap
import java.util.UUID

/**
 * Process-local one-shot handoff for CameraX bytes.
 *
 * The Activity and the thin host share one application process, so the Intent
 * only needs to carry an opaque token. This avoids putting a raw image into a
 * Binder transaction while still avoiding disk persistence.
 */
object AndroidPhotoCaptureResultStore {
    private const val MAX_RESULTS = 2
    private const val MAX_AGE_MILLIS = 5 * 60 * 1000L

    private data class Entry(val bytes: ByteArray, val createdAtMillis: Long)

    private val lock = Any()
    private val entries = LinkedHashMap<String, Entry>()

    @JvmStatic
    fun put(bytes: ByteArray): String = synchronized(lock) {
        require(bytes.isNotEmpty()) { "Capture result must not be empty" }
        cleanupExpired(System.currentTimeMillis())
        while (entries.size >= MAX_RESULTS) {
            entries.remove(entries.keys.first())?.bytes?.fill(0)
        }
        UUID.randomUUID().toString().also { token ->
            entries[token] = Entry(bytes.copyOf(), System.currentTimeMillis())
        }
    }

    @JvmStatic
    fun take(token: String): ByteArray? = synchronized(lock) {
        cleanupExpired(System.currentTimeMillis())
        entries.remove(token)?.bytes?.also { stored ->
            val result = stored.copyOf()
            stored.fill(0)
            return@synchronized result
        }
    }

    private fun cleanupExpired(nowMillis: Long) {
        val expired = entries.entries
            .filter { nowMillis - it.value.createdAtMillis >= MAX_AGE_MILLIS }
            .map { it.key }
        expired.forEach { token -> entries.remove(token)?.bytes?.fill(0) }
    }
}
