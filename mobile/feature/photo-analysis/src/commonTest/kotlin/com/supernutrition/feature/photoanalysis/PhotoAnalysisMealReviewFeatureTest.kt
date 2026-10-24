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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoAnalysisMealReviewFeatureTest {
    @Test
    fun `review source ingredient labels containing commas remain one explicit ingredient`() {
        val feature = feature()
        prepare(feature)
        val request = RequestId("comma-label")
        val evidence = draft().photoEvidence.first()
        feature.dispatch(PhotoAnalysisEvent.AnalysisStarted(request))
        feature.dispatch(
            PhotoAnalysisEvent.ReviewEvidenceAvailable(
                request,
                draft().copy(
                    photoEvidence = listOf(evidence.copy(candidate = evidence.candidate.copy(label = "Rice, white"))),
                ),
            ),
        )
        assertTrue(feature.saveReviewedMeal(confirmation().copy(photoEvidenceComponentIds = listOf("photo-1"))))
        assertEquals(listOf("Rice, white"), feature.mealDiary.meals.value.single().ingredients)
    }

    @Test
    fun `background and ignored replacement preserve the saved review identity while corrections replace it`() {
        val feature = feature()
        prepare(feature)
        val review = confirmation().copy(note = "First note")
        assertTrue(feature.saveReviewedMeal(review))
        val original = feature.mealDiary.meals.value.single()
        val photo = assertIs<PhotoAnalysisState.Ready>(feature.state).input.ref
        feature.dispatch(PhotoAnalysisEvent.Backgrounded)
        feature.dispatch(PhotoAnalysisEvent.ReplacePhoto(photo))
        assertTrue(feature.saveReviewedMeal(review.copy(note = "Corrected note")))
        val corrected = feature.mealDiary.meals.value.single()
        assertEquals(original.id, corrected.id)
        assertEquals("Corrected note", corrected.note)
        feature.dispatch(PhotoAnalysisEvent.Restored)
        assertTrue(feature.saveReviewedMeal(review.copy(note = "Final note")))
        assertEquals(original.id, feature.mealDiary.meals.value.single().id)
        assertEquals("Final note", feature.mealDiary.meals.value.single().note)
    }

    @Test
    fun `a failed review correction retains the previous meal and retries without creating another`() {
        var writable = true
        val diary = MealDiary(
            recordContext = { MealRecordContext("meal", 20_020, "13:15") },
            store = object : MealDiaryStore {
                override fun load(): String? = null
                override fun save(encoded: String): Boolean = writable
            },
        )
        val feature = PhotoAnalysisFeature(
            AttemptId("correction"),
            PhotoLeaseStore(),
            PhotoSessionStore(),
            mealDiary = diary,
        )
        prepare(feature)
        assertTrue(feature.saveReviewedMeal(confirmation().copy(note = "Original")))
        writable = false
        assertFalse(feature.saveReviewedMeal(confirmation().copy(note = "Corrected")))
        assertEquals("Original", diary.meals.value.single().note)
        writable = true
        assertTrue(feature.saveReviewedMeal(confirmation().copy(note = "Corrected")))
        assertEquals("meal", diary.meals.value.single().id)
        assertEquals("Corrected", diary.meals.value.single().note)
    }

    @Test
    fun `an empty custom title has an honest default and excessive review data is not stored`() {
        val feature = feature()
        prepare(feature)
        assertTrue(feature.saveReviewedMeal(confirmation().copy(dishLabel = " ", note = "Explicit note")))
        assertEquals("Custom meal", feature.mealDiary.meals.value.single().name)
        assertFalse(
            feature.saveReviewedMeal(confirmation().copy(note = "x".repeat(MealDraftLimits.NOTE_MAX_LENGTH + 1))),
        )
        assertEquals("Explicit note", feature.mealDiary.meals.value.single().note)
    }

    @Test
    fun `explicit review save creates one diary meal and survives leaving capture`() {
        val feature = feature()
        val reviewed = confirmation().copy(addedIngredientLabels = listOf(" Rice ", "Carrot"), note = "Less sauce")
        assertFalse(feature.saveReviewedMeal(reviewed))
        prepare(feature)
        assertTrue(feature.saveReviewedMeal(reviewed))
        assertTrue(feature.saveReviewedMeal(reviewed))
        val saved = feature.mealDiary.meals.value.single()
        assertEquals("Custom meal", saved.name)
        assertEquals(listOf("Rice", "Carrot"), saved.ingredients)
        assertEquals("Less sauce", saved.note)
        assertEquals(SavedMealSource.PHOTO, saved.source)
        feature.dispatch(PhotoAnalysisEvent.Cancel)
        assertEquals(listOf(saved), feature.mealDiary.meals.value)
        assertNull(feature.confirmedMealReview.value)
    }

    @Test
    fun `saving photo evidence uses source labels and a new photo creates a separate meal`() {
        val feature = feature()
        prepare(feature)
        val request = RequestId("review")
        feature.dispatch(PhotoAnalysisEvent.AnalysisStarted(request))
        feature.dispatch(PhotoAnalysisEvent.ReviewEvidenceAvailable(request, draft()))
        assertTrue(
            feature.saveReviewedMeal(
                confirmation().copy(
                    dishCandidateId = "dish-1",
                    dishLabel = "Untrusted UI title",
                    photoEvidenceComponentIds = listOf("photo-1"),
                    addedIngredientLabels = listOf("Carrot"),
                ),
            ),
        )
        val first = feature.mealDiary.meals.value.single()
        assertEquals("Salmon bowl", first.name)
        assertEquals(listOf("Salmon", "Carrot"), first.ingredients)
        feature.dispatch(PhotoAnalysisEvent.ScanAgain)
        prepare(feature)
        assertTrue(feature.saveReviewedMeal(confirmation().copy(note = "Second meal")))
        assertEquals(2, feature.mealDiary.meals.value.size)
    }

    @Test
    fun `a storage failure keeps the reviewed meal available for retry`() {
        var writable = false
        val diary = MealDiary(
            recordContext = { MealRecordContext("meal", 20_020, "13:15") },
            store = object : MealDiaryStore {
                override fun load(): String? = null
                override fun save(encoded: String): Boolean = writable
            },
        )
        val feature = PhotoAnalysisFeature(AttemptId("save"), PhotoLeaseStore(), PhotoSessionStore(), mealDiary = diary)
        prepare(feature)
        val review = confirmation().copy(addedIngredientLabels = listOf("Rice"))
        assertFalse(feature.saveReviewedMeal(review))
        assertIs<PhotoAnalysisState.Ready>(feature.state)
        assertTrue(diary.meals.value.isEmpty())
        writable = true
        assertTrue(feature.saveReviewedMeal(review))
        assertEquals(1, diary.meals.value.size)
    }

    @Test
    fun `text save is reachable without a photo and cannot create nutrient reward evidence`() {
        val feature = feature()
        assertFalse(feature.saveTextMeal(MealDraft()))
        assertTrue(feature.saveTextMeal(MealDraft("Green bowl", "300", "Spinach, avocado, chickpeas")))
        val saved = feature.mealDiary.meals.value.single()
        assertEquals(SavedMealSource.TEXT, saved.source)
        assertEquals(listOf("Spinach", "avocado", "chickpeas"), saved.ingredients)
        assertEquals(PhotoAnalysisState.Capture, feature.state)
    }

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
        feature.updateManualIngredientQuery("Current note")
        assertTrue(feature.updateMealReviewSelection(selection))
        components.clear()
        pantry.clear()
        feature.dispatch(PhotoAnalysisEvent.Backgrounded)
        val review = assertIs<PhotoAnalysisState.CandidateReview>(feature.currentWorkState())
        listOf(
            PhotoAnalysisEvent.RestoreFromSnapshot(feature.snapshotForPersistence()),
            PhotoAnalysisEvent.ReplacePhoto(review.ref),
            PhotoAnalysisEvent.ScanAgain,
        ).forEach { ignoredEvent ->
            feature.dispatch(ignoredEvent)
            assertEquals(review, feature.currentWorkState(), "Ignored $ignoredEvent must retain the live review")
            assertEquals("Current note", feature.manualIngredientStateFlow.value.query)
        }
        assertEquals("dish-1", feature.mealReviewSelection.value.dishId)
        assertEquals(setOf("photo-1", "photo-2"), feature.mealReviewSelection.value.componentIds)
        assertEquals(setOf("carrot", "salt"), feature.mealReviewSelection.value.pantryIds)
        assertTrue(feature.updateMealReviewSelection(feature.mealReviewSelection.value.copy(dishId = null)))
        feature.dispatch(PhotoAnalysisEvent.Restored)
        assertEquals("Current note", feature.manualIngredientStateFlow.value.query)
        assertEquals(
            PhotoAnalysisMealReviewSelection(
                componentIds = setOf("photo-1", "photo-2"),
                pantryIds = setOf("carrot", "salt"),
            ),
            feature.mealReviewSelection.value,
        )
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
