package com.supernutrition.feature.photoanalysis

import com.supernutrition.core.config.InMemoryPrivateDocumentStore
import com.supernutrition.core.config.PrivateDocumentId
import com.supernutrition.core.config.PrivateDocumentStore

/** The composition root alone selects durable adapters; ordinary test sessions remain isolated. */
class LocalUserData(
    val catalogSession: MealCatalogSession = MealCatalogSession(),
    stores: (PrivateDocumentId) -> PrivateDocumentStore = { InMemoryPrivateDocumentStore() },
) {
    constructor(stores: (PrivateDocumentId) -> PrivateDocumentStore) : this(MealCatalogSession(), stores)

    val mealDiary = MealDiary(
        store = PrivateMealDiaryStore(stores(PrivateDocumentId.MEAL_DIARY)),
        catalogSession = catalogSession,
    )
    val profile = LocalProfile(stores(PrivateDocumentId.PROFILE_NAME))
}

class PrivateMealDiaryStore(private val document: PrivateDocumentStore) : MealDiaryStore {
    override fun load(): String? = document.load()

    override fun save(encoded: String): Boolean = document.save(encoded)
}
