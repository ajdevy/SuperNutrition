package com.supernutrition.sharedkit

import com.supernutrition.feature.photoanalysis.MealDraft
import com.supernutrition.feature.photoanalysis.saveTextMeal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SharedKitMealDiarySessionTest {
    @Test
    fun profileSharesTheExplicitPhotoSessionDiary() {
        val rewards = SharedKitRewardsSession()
        val photo = SharedKitPhotoAnalysisSession("shared-diary")
        val profile = SharedKitProfileSession(rewards, photo)
        assertSame(photo.feature.mealDiary, profile.mealDiary)
        assertSame(rewards.feature, profile.rewardsFeature)
        assertTrue(photo.feature.saveTextMeal(MealDraft("Soup", "250", "Carrot, potato")))
        val diary = assertNotNull(profile.mealDiary)
        val meal = diary.meals.value.single()
        assertEquals("Soup", meal.name)
        assertTrue(diary.update(meal.id, MealDraft("Vegetable soup")))
        assertEquals("Vegetable soup", photo.feature.mealDiary.meals.value.single().name)
        assertTrue(diary.delete(meal.id))
        assertTrue(photo.feature.mealDiary.meals.value.isEmpty())
    }

    @Test
    fun legacyAndExplicitlyAbsentPhotoSessionsRemainSessionScoped() {
        val rewards = SharedKitRewardsSession()
        val legacy = SharedKitProfileSession(rewards)
        val absent = SharedKitProfileSession(rewards, null)
        assertNull(legacy.mealDiary)
        assertNull(absent.mealDiary)
        assertFalse(legacy.updateDisplayName(" "))
        assertTrue(legacy.updateDisplayName(" Alex "))
        assertEquals("Alex", legacy.displayName)
        assertEquals("", absent.displayName)
    }
}
