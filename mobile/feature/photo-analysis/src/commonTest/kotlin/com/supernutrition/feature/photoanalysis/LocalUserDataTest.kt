package com.supernutrition.feature.photoanalysis

import com.supernutrition.core.config.InMemoryPrivateDocumentStore
import com.supernutrition.core.config.PrivateDocumentId
import com.supernutrition.core.config.RevisionCheckedPrivateDocumentStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LocalUserDataTest {
    @Test
    fun `independent durable owners surface stale writes and cannot resurrect deleted meals or names`() {
        val stores = PrivateDocumentId.entries.associateWith { InMemoryPrivateDocumentStore() }
        fun owner() = LocalUserData { id ->
            val document = stores.getValue(id)
            RevisionCheckedPrivateDocumentStore(document::load, document::save)
        }
        val first = owner()
        val stale = owner()
        assertTrue(first.profile.updateDisplayName("Accepted name"))
        assertFalse(stale.profile.updateDisplayName("Stale name"))
        assertEquals("", stale.profile.displayName.value)
        val accepted = assertNotNull(first.mealDiary.add(MealDraft("Accepted meal"), SavedMealSource.TEXT))
        assertNull(stale.mealDiary.add(MealDraft("Stale meal"), SavedMealSource.TEXT))
        assertEquals(MealDiaryFailure.WRITE_FAILED, stale.mealDiary.failure.value)
        val beforeDeletion = owner()
        assertEquals(listOf(accepted), beforeDeletion.mealDiary.meals.value)
        assertEquals("Accepted name", beforeDeletion.profile.displayName.value)
        assertTrue(first.mealDiary.delete(accepted.id))
        assertTrue(first.profile.updateDisplayName(""))
        assertNull(beforeDeletion.mealDiary.add(MealDraft("Resurrected meal"), SavedMealSource.TEXT))
        assertFalse(beforeDeletion.profile.updateDisplayName("Resurrected name"))
        assertEquals(MealDiaryFailure.WRITE_FAILED, beforeDeletion.mealDiary.failure.value)
        val restored = owner()
        assertTrue(restored.mealDiary.meals.value.isEmpty())
        assertEquals("", restored.profile.displayName.value)
    }

    @Test
    fun `catalog ownership preserves positional and trailing lambda storage constructors`() {
        val stores = PrivateDocumentId.entries.associateWith { InMemoryPrivateDocumentStore() }
        val positional = LocalUserData(stores::getValue)
        val trailing = LocalUserData { stores.getValue(it) }
        assertTrue(positional.profile.updateDisplayName("Alex"))
        assertEquals("Alex", LocalUserData { stores.getValue(it) }.profile.displayName.value)
        assertEquals("", trailing.profile.displayName.value)
        val catalog = FeatureCatalogFixture().openSession()
        val attached = LocalUserData(catalog, stores::getValue)
        assertSame(catalog, attached.catalogSession)
        assertSame(catalog, attached.mealDiary.catalogSession)
        catalog.close()
    }

    @Test
    fun `new app compositions restore both stores and preserve explicit deletions`() {
        val stores = PrivateDocumentId.entries.associateWith { InMemoryPrivateDocumentStore() }
        val first = LocalUserData(stores::getValue)
        assertTrue(first.profile.updateDisplayName("Alex"))
        val meal = assertNotNull(first.mealDiary.add(MealDraft("Soup"), SavedMealSource.TEXT))
        val restored = LocalUserData(stores::getValue)
        assertEquals("Alex", restored.profile.displayName.value)
        assertEquals(listOf(meal), restored.mealDiary.meals.value)
        assertTrue(restored.profile.updateDisplayName(""))
        assertTrue(restored.mealDiary.delete(meal.id))
        val afterDeletion = LocalUserData(stores::getValue)
        assertEquals("", afterDeletion.profile.displayName.value)
        assertTrue(afterDeletion.mealDiary.meals.value.isEmpty())
    }

    @Test
    fun `default test compositions remain isolated`() {
        val first = LocalUserData()
        assertTrue(first.profile.updateDisplayName("Alex"))
        assertNotNull(first.mealDiary.add(MealDraft("Soup"), SavedMealSource.TEXT))
        val second = LocalUserData()
        assertEquals("", second.profile.displayName.value)
        assertTrue(second.mealDiary.meals.value.isEmpty())
    }
}
