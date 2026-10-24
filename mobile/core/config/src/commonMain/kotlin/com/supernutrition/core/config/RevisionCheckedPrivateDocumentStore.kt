package com.supernutrition.core.config

/**
 * Rejects replacements based on an owner's stale document snapshot, preserving the existing storage port.
 * Callers must serialize each complete load/save operation under the shared platform document lock.
 */
class RevisionCheckedPrivateDocumentStore(
    private val readDocument: () -> String?,
    private val writeDocument: (String) -> Boolean,
) : PrivateDocumentStore {
    private var hasLoaded = false
    private var expected: String? = null

    override fun load(): String? {
        val encoded = readDocument()
        expected = encoded
        hasLoaded = true
        return encoded
    }

    override fun save(encoded: String): Boolean {
        val actual = readDocument()
        if (hasLoaded && actual != expected) return false
        if (!writeDocument(encoded)) return false
        expected = encoded
        hasLoaded = true
        return true
    }
}
