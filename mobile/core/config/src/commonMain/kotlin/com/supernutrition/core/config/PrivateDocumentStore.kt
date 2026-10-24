package com.supernutrition.core.config

/** Closed app-owned names: user content can never choose a filesystem path. */
enum class PrivateDocumentId(val fileName: String) {
    MEAL_DIARY("meal-diary.txt"),
    PROFILE_NAME("profile-name.txt"),
}

/** App-private, backup-excluded documents. A failed replacement preserves the previous document. */
interface PrivateDocumentStore {
    fun load(): String?

    fun save(encoded: String): Boolean
}

object PrivateDocumentEncoding {
    const val MAX_BYTES = 16 * 1024 * 1024

    fun encode(value: String): ByteArray = value.encodeToByteArray(throwOnInvalidSequence = true).also {
        require(it.size <= MAX_BYTES) { "Private document exceeds its size limit" }
    }

    fun decode(bytes: ByteArray): String {
        require(bytes.size <= MAX_BYTES) { "Private document exceeds its size limit" }
        return bytes.decodeToString(throwOnInvalidSequence = true)
    }
}

class InMemoryPrivateDocumentStore(initialEncoded: String? = null) : PrivateDocumentStore {
    private var encoded = initialEncoded

    override fun load(): String? = encoded

    override fun save(encoded: String): Boolean {
        this.encoded = encoded
        return true
    }
}
