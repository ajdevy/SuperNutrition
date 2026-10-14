package com.supernutrition.feature.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Protects the shared host contract separating user choices from detected evidence. */
class PhotoAnalysisMealReviewPayloadTest {
    @Test
    fun `a new review has no implicit dish or ingredient selections`() {
        val selection = PhotoAnalysisMealReviewSelection()
        assertNull(selection.dishId)
        assertFalse(selection.customMeal)
        assertTrue(selection.componentIds.isEmpty())
        assertTrue(selection.pantryIds.isEmpty())
    }

    @Test
    fun `review payload keeps opaque evidence identities apart from localized additions`() {
        val selection = PhotoAnalysisMealReviewSelection(
            dishId = "dish:salmon-bowl",
            componentIds = setOf("photo:broccoli"),
            pantryIds = setOf("carrot"),
        )
        val confirmed = PhotoAnalysisMealReviewConfirmation(
            dishCandidateId = selection.dishId,
            dishLabel = "Salmone e riso",
            photoEvidenceComponentIds = selection.componentIds.toList(),
            addedIngredientLabels = listOf("Carota"),
            note = "Meno salsa",
        )
        assertFalse(selection.customMeal)
        assertEquals(setOf("carrot"), selection.pantryIds)
        assertEquals("dish:salmon-bowl", confirmed.dishCandidateId)
        assertEquals("Salmone e riso", confirmed.dishLabel)
        assertEquals(listOf("photo:broccoli"), confirmed.photoEvidenceComponentIds)
        assertEquals(listOf("Carota"), confirmed.addedIngredientLabels)
        assertEquals("Meno salsa", confirmed.note)
    }
}
