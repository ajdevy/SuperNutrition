package com.supernutrition.feature.photoanalysis

/** Storage of identity-only diary records; adapters never receive photo references or bytes. */
interface MealDiaryStore {
    fun load(): String?

    fun save(encoded: String): Boolean
}

/** Session-scoped default. Durable retention requires an explicit production adapter. */
class InMemoryMealDiaryStore(initialEncoded: String? = null) : MealDiaryStore {
    private var encoded = initialEncoded

    override fun load(): String? = encoded

    override fun save(encoded: String): Boolean {
        this.encoded = encoded
        return true
    }
}

enum class MealDiaryFailure { LOAD_FAILED, CORRUPT_STORAGE, WRITE_FAILED }
