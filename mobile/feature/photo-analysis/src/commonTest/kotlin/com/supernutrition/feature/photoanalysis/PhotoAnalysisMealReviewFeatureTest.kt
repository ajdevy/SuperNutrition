package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.inference.CalibratedConfidence
import com.supernutrition.domain.inference.ComponentAmbiguity
import com.supernutrition.domain.inference.ComponentVisibility
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.INFERENCE_EVIDENCE_CONTRACT_V2
import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.InferenceV2Provenance
import com.supernutrition.domain.inference.LocalizedCandidateIdentity
import com.supernutrition.domain.inference.MassEvidence
import com.supernutrition.domain.inference.MassNotEvaluableReason
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import com.supernutrition.domain.inference.VisibleComponent
import com.supernutrition.domain.photoanalysis.AttemptId
import com.supernutrition.domain.photoanalysis.CandidateReviewDraft
import com.supernutrition.domain.photoanalysis.ImageOrientation
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisEvent
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoLeaseStore
import com.supernutrition.domain.photoanalysis.PhotoSessionStore
import com.supernutrition.domain.photoanalysis.RequestId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoAnalysisMealReviewFeatureTest {
    @Test
    fun `capture cannot confirm a draft and custom meal requires content`() {
        val feature = feature()
        assertFalse(feature.confirmMealReview(confirmation().copy(note = "Rice")))
        prepare(feature)
        assertFalse(feature.confirmMealReview(confirmation()))
        assertFalse(feature.confirmMealReview(confirmation().copy(addedIngredientLabels = listOf(" "))))
        assertFalse(feature.confirmMealReview(confirmation().copy(dishCandidateId = "unknown")))
        assertFalse(feature.confirmMealReview(confirmation().copy(photoEvidenceComponentIds = listOf("unknown"))))
        assertNull(feature.confirmedMealReview.value)
        assertTrue(feature.confirmMealReview(confirmation().copy(note = "  Less sauce  ")))
        assertEquals("Less sauce", feature.confirmedMealReview.value?.note)
    }

    @Test
    fun `review validates evidence IDs and preserves the candidate label from its source`() {
        val feature = feature()
        prepare(feature)
        val request = RequestId("review")
        feature.dispatch(PhotoAnalysisEvent.AnalysisStarted(request))
        feature.dispatch(PhotoAnalysisEvent.ReviewEvidenceAvailable(request, draft()))
        assertFalse(feature.confirmMealReview(confirmation().copy(dishCandidateId = "unknown")))
        assertFalse(feature.confirmMealReview(confirmation().copy(photoEvidenceComponentIds = listOf("unknown"))))
        assertTrue(
            feature.confirmMealReview(
                confirmation().copy(
                    dishCandidateId = "dish-1",
                    dishLabel = "Changed UI label",
                    photoEvidenceComponentIds = listOf("photo-2", "photo-1", "photo-2"),
                    addedIngredientLabels = listOf(" Carrot ", "Carrot", " "),
                ),
            ),
        )
        val saved = feature.confirmedMealReview.value
        assertEquals("Salmon bowl", saved?.dishLabel)
        assertEquals(listOf("photo-1", "photo-2"), saved?.photoEvidenceComponentIds)
        assertEquals(listOf("Carrot"), saved?.addedIngredientLabels)
        assertTrue(feature.confirmMealReview(confirmation().copy(photoEvidenceComponentIds = listOf("photo-1"))))
        assertEquals(null, feature.confirmedMealReview.value?.dishCandidateId)
    }

    @Test
    fun `retake cancel and new photo clear previous review and manual input`() {
        listOf(PhotoAnalysisEvent.ScanAgain, PhotoAnalysisEvent.Cancel).forEach { event ->
            val feature = feature()
            prepare(feature)
            feature.updateManualIngredientQuery("Rice")
            feature.addManualIngredients()
            assertTrue(feature.confirmMealReview(confirmation().copy(addedIngredientLabels = listOf("Rice"))))
            feature.dispatch(event)
            assertNull(feature.confirmedMealReview.value)
            assertTrue(feature.manualIngredientStateFlow.value.ingredients.isEmpty())
            assertEquals("", feature.manualIngredientStateFlow.value.query)
        }
        val feature = feature()
        feature.updateManualIngredientQuery("Old note")
        prepare(feature)
        assertEquals("", feature.manualIngredientStateFlow.value.query)
    }

    @Test
    fun `accepted replacement clears the previous confirmation and note`() {
        val feature = feature()
        prepare(feature)
        feature.updateManualIngredientQuery("Previous meal")
        assertTrue(feature.updateMealReviewSelection(PhotoAnalysisMealReviewSelection(customMeal = true)))
        assertTrue(feature.confirmMealReview(confirmation().copy(note = "Previous meal")))
        feature.dispatch(PhotoAnalysisEvent.ReplacePhoto(feature.issuePhotoRef("replacement")))
        assertNull(feature.confirmedMealReview.value)
        assertEquals("", feature.manualIngredientStateFlow.value.query)
        assertEquals(PhotoAnalysisMealReviewSelection(), feature.mealReviewSelection.value)
    }

    @Test
    fun `ignored photo replacement preserves the current review and note`() {
        val feature = feature()
        prepare(feature)
        val currentPhoto = (feature.state as PhotoAnalysisState.Ready).input.ref
        feature.updateManualIngredientQuery("Current meal")
        val selection = PhotoAnalysisMealReviewSelection(customMeal = true, pantryIds = setOf("carrot"))
        assertTrue(feature.updateMealReviewSelection(selection))
        val confirmation = confirmation().copy(note = "Current meal")
        assertTrue(feature.confirmMealReview(confirmation))
        feature.dispatch(PhotoAnalysisEvent.ReplacePhoto(currentPhoto))
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(feature.issuePhotoRef("ignored")))
        assertEquals(confirmation, feature.confirmedMealReview.value)
        assertEquals("Current meal", feature.manualIngredientStateFlow.value.query)
        assertEquals(selection, feature.mealReviewSelection.value)
    }

    @Test
    fun `selection rejects unavailable evidence and conflicting dish choices`() {
        val feature = feature()
        assertFalse(feature.updateMealReviewSelection(PhotoAnalysisMealReviewSelection(customMeal = true)))
        prepare(feature)
        assertTrue(feature.updateMealReviewSelection(PhotoAnalysisMealReviewSelection(customMeal = true)))
        assertFalse(
            feature.updateMealReviewSelection(PhotoAnalysisMealReviewSelection(dishId = "dish-1", customMeal = true)),
        )
        assertFalse(feature.updateMealReviewSelection(PhotoAnalysisMealReviewSelection(dishId = "unknown")))
        assertFalse(
            feature.updateMealReviewSelection(PhotoAnalysisMealReviewSelection(componentIds = setOf("unknown"))),
        )
        assertEquals(PhotoAnalysisMealReviewSelection(customMeal = true), feature.mealReviewSelection.value)
    }

    @Test
    fun `retained selection survives background and copies mutable evidence sets`() {
        val feature = feature()
        prepare(feature)
        val request = RequestId("review")
        feature.dispatch(PhotoAnalysisEvent.AnalysisStarted(request))
        feature.dispatch(PhotoAnalysisEvent.ReviewEvidenceAvailable(request, draft()))
        val components = mutableSetOf("photo-1", "photo-2")
        val pantry = mutableSetOf("carrot", "salt")
        val selection = PhotoAnalysisMealReviewSelection("dish-1", componentIds = components, pantryIds = pantry)
        assertTrue(feature.updateMealReviewSelection(selection))
        components.clear()
        pantry.clear()
        feature.dispatch(PhotoAnalysisEvent.Backgrounded)
        assertEquals("dish-1", feature.mealReviewSelection.value.dishId)
        assertEquals(setOf("photo-1", "photo-2"), feature.mealReviewSelection.value.componentIds)
        assertEquals(setOf("carrot", "salt"), feature.mealReviewSelection.value.pantryIds)
        assertTrue(feature.updateMealReviewSelection(feature.mealReviewSelection.value.copy(dishId = null)))
        feature.dispatch(PhotoAnalysisEvent.Restored)
        assertEquals(setOf("photo-1", "photo-2"), feature.mealReviewSelection.value.componentIds)
        feature.dispatch(PhotoAnalysisEvent.ScanAgain)
        assertEquals(PhotoAnalysisMealReviewSelection(), feature.mealReviewSelection.value)
    }

    @Test
    fun `accepted reanalysis clears stale evidence but preserves explicit manual choices`() {
        val feature = feature()
        prepare(feature)
        val firstRequest = RequestId("first-review")
        feature.dispatch(PhotoAnalysisEvent.AnalysisStarted(firstRequest))
        feature.dispatch(PhotoAnalysisEvent.ReviewEvidenceAvailable(firstRequest, draft()))
        feature.updateManualIngredientQuery("Rice")
        feature.addManualIngredients()
        val ingredients = feature.manualIngredientStateFlow.value.ingredients
        feature.updateManualIngredientQuery("Less sauce")
        assertTrue(
            feature.updateMealReviewSelection(
                PhotoAnalysisMealReviewSelection("dish-1", componentIds = setOf("photo-1"), pantryIds = setOf("salt")),
            ),
        )
        assertTrue(feature.confirmMealReview(confirmation().copy(photoEvidenceComponentIds = listOf("photo-1"))))
        feature.dispatch(PhotoAnalysisEvent.GridCellSelected(0))
        assertNull(feature.confirmedMealReview.value)
        assertNull(feature.mealReviewSelection.value.dishId)
        assertEquals(emptySet(), feature.mealReviewSelection.value.componentIds)
        assertEquals(setOf("salt"), feature.mealReviewSelection.value.pantryIds)
        assertEquals("Less sauce", feature.manualIngredientStateFlow.value.query)
        assertEquals(ingredients, feature.manualIngredientStateFlow.value.ingredients)
        val nextRequest = RequestId("second-review")
        feature.dispatch(PhotoAnalysisEvent.AnalysisStarted(nextRequest))
        val newEvidence = draft().copy(
            mainDishProposals = emptyList(),
            photoEvidence = draft().photoEvidence.take(1).map { it.copy(componentId = "new-photo") },
        )
        feature.dispatch(PhotoAnalysisEvent.ReviewEvidenceAvailable(nextRequest, newEvidence))
        assertTrue(
            feature.updateMealReviewSelection(
                feature.mealReviewSelection.value.copy(componentIds = setOf("new-photo")),
            ),
        )
        assertTrue(feature.confirmMealReview(confirmation().copy(photoEvidenceComponentIds = listOf("new-photo"))))
    }

    @Test
    fun `ignored reanalysis preserves current manual selection and confirmation`() {
        val feature = feature()
        prepare(feature)
        val selection = PhotoAnalysisMealReviewSelection(customMeal = true, pantryIds = setOf("salt"))
        feature.updateManualIngredientQuery("Current note")
        assertTrue(feature.updateMealReviewSelection(selection))
        val saved = confirmation().copy(note = "Current note")
        assertTrue(feature.confirmMealReview(saved))
        feature.dispatch(PhotoAnalysisEvent.GridCellSelected(0))
        assertEquals(selection, feature.mealReviewSelection.value)
        assertEquals(saved, feature.confirmedMealReview.value)
        assertEquals("Current note", feature.manualIngredientStateFlow.value.query)
    }

    private fun feature() = PhotoAnalysisFeature(AttemptId("review"), PhotoLeaseStore(), PhotoSessionStore())

    private fun prepare(feature: PhotoAnalysisFeature) {
        val ref = feature.issuePhotoRef("photo")
        feature.dispatch(PhotoAnalysisEvent.PhotoCaptured(ref))
        feature.dispatch(
            PhotoAnalysisEvent.PreparationSucceeded(
                NormalizedPhotoInput(
                    ref,
                    byteArrayOf(1),
                    1,
                    1,
                    ImageOrientation.UP,
                    "sRGB",
                    emptySet(),
                    "a".repeat(64),
                ),
            ),
        )
    }

    private fun confirmation() = PhotoAnalysisMealReviewConfirmation(null, "Custom meal", emptyList(), emptyList(), "")

    private fun draft() = CandidateReviewDraft(
        mainDishProposals = listOf(
            InferenceCandidate(
                "dish-1",
                "Salmon bowl",
                1,
                Confidence(0.9, ConfidenceBand.HIGH),
                emptyList(),
                InferenceProvenance(
                    InferenceRoute.LOCAL,
                    "dish-engine",
                    "dish-model",
                    "calibration",
                    PHOTO_INFERENCE_CONTRACT_V1,
                ),
            ),
        ),
        photoEvidence = listOf("photo-1", "photo-2").map { id ->
            VisibleComponent(
                componentId = id,
                candidate = LocalizedCandidateIdentity("salmon", "Salmon", "en"),
                confidence = CalibratedConfidence(0.9, ConfidenceBand.HIGH),
                visibility = ComponentVisibility.VISIBLE,
                ambiguity = ComponentAmbiguity.NONE,
                mass = MassEvidence.NotEvaluable(MassNotEvaluableReason.MISSING_CALIBRATION, "not-evaluable"),
                provenance = InferenceV2Provenance(
                    InferenceRoute.LOCAL,
                    "engine",
                    "model",
                    "taxonomy",
                    "calibration",
                    INFERENCE_EVIDENCE_CONTRACT_V2,
                ),
            )
        },
    )
}
