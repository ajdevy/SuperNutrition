package com.supernutrition.domain.photoanalysis

import com.supernutrition.domain.inference.CalibratedConfidence
import com.supernutrition.domain.inference.ComponentAmbiguity
import com.supernutrition.domain.inference.ComponentVisibility
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.GramRange
import com.supernutrition.domain.inference.INFERENCE_EVIDENCE_CONTRACT_V2
import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.InferenceV2Provenance
import com.supernutrition.domain.inference.LocalizedCandidateIdentity
import com.supernutrition.domain.inference.MassCalibrationAssumption
import com.supernutrition.domain.inference.MassEvidence
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import com.supernutrition.domain.inference.VisibleComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class CandidateReviewSelectionTest {
    @Test
    fun `review starts unselected and keeps v1 dishes separate from v2 photo evidence`() {
        val dishes = listOf(dish("dish-1", 1), dish("dish-2", 2))
        val evidence = listOf(component("component-1", "ingredient:salmon"))

        val state = CandidateReviewState(CandidateReviewDraft(dishes, evidence))

        assertNull(state.selection.mainDishCandidateId)
        assertEquals(emptySet(), state.selection.photoEvidenceComponentIds)
        assertEquals(dishes, state.draft.mainDishProposals)
        assertEquals(evidence, state.draft.photoEvidence)
        assertSame(evidence.single(), state.draft.photoEvidence.single())
    }

    @Test
    fun `review rejects more than three or non-distinct dish and evidence inputs`() {
        assertFailsWith<IllegalArgumentException> {
            CandidateReviewDraft(
                listOf(dish("dish-1", 1), dish("dish-2", 2), dish("dish-3", 3), dish("dish-4", 4)),
                emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CandidateReviewDraft(listOf(dish("dish-1", 1), dish("dish-1", 2)), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            CandidateReviewDraft(listOf(dish("dish-1", 1), dish("dish-2", 1)), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            CandidateReviewDraft(listOf(dish("dish-2", 2), dish("dish-1", 1)), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            CandidateReviewDraft(
                emptyList(),
                listOf(component("component-1", "ingredient:salmon"), component("component-1", "ingredient:rice")),
            )
        }
    }

    @Test
    fun `dish selection is explicit reversible and ignores an unknown proposal`() {
        val reducer = CandidateReviewSelectionReducer
        val initial = CandidateReviewState(CandidateReviewDraft(listOf(dish("dish-1", 1)), emptyList()))

        val ignored = reducer.reduce(initial, CandidateReviewAction.SelectMainDish("unknown"))
        val selected = reducer.reduce(initial, CandidateReviewAction.SelectMainDish("dish-1"))
        val cleared = reducer.reduce(selected, CandidateReviewAction.ClearMainDish)

        assertEquals(initial, ignored)
        assertEquals("dish-1", selected.selection.mainDishCandidateId)
        assertNull(cleared.selection.mainDishCandidateId)
    }

    @Test
    fun `photo evidence toggles reversibly and an unknown component is ignored`() {
        val reducer = CandidateReviewSelectionReducer
        val initial = CandidateReviewState(
            CandidateReviewDraft(emptyList(), listOf(component("component-1", "ingredient:salmon"))),
        )

        val ignored = reducer.reduce(initial, CandidateReviewAction.TogglePhotoEvidence("unknown"))
        val selected = reducer.reduce(initial, CandidateReviewAction.TogglePhotoEvidence("component-1"))
        val cleared = reducer.reduce(selected, CandidateReviewAction.TogglePhotoEvidence("component-1"))

        assertEquals(initial, ignored)
        assertEquals(setOf("component-1"), selected.selection.photoEvidenceComponentIds)
        assertEquals(emptySet(), cleared.selection.photoEvidenceComponentIds)
    }

    @Test
    fun `clearing selections removes only explicit session choices`() {
        val initial = CandidateReviewState(
            CandidateReviewDraft(listOf(dish("dish-1", 1)), listOf(component("component-1", "ingredient:salmon"))),
        )
        val selected = CandidateReviewSelectionReducer.reduce(
            CandidateReviewSelectionReducer.reduce(
                initial,
                CandidateReviewAction.SelectMainDish("dish-1"),
            ),
            CandidateReviewAction.TogglePhotoEvidence("component-1"),
        )

        val cleared = CandidateReviewSelectionReducer.reduce(selected, CandidateReviewAction.ClearSelections)

        assertEquals(initial.draft, cleared.draft)
        assertEquals(CandidateReviewSelection(), cleared.selection)
    }

    @Test
    fun `confirmation requires a selected dish and returns only selected stable ids in evidence order`() {
        val reducer = CandidateReviewSelectionReducer
        val initial = CandidateReviewState(
            CandidateReviewDraft(
                mainDishProposals = listOf(dish("dish-1", 1)),
                photoEvidence = listOf(
                    component("component-2", "ingredient:rice"),
                    component("component-1", "ingredient:salmon"),
                ),
            ),
        )
        val ingredientsSelected = reducer.reduce(
            reducer.reduce(initial, CandidateReviewAction.TogglePhotoEvidence("component-1")),
            CandidateReviewAction.TogglePhotoEvidence("component-2"),
        )
        val confirmed = reducer.reduce(ingredientsSelected, CandidateReviewAction.SelectMainDish("dish-1"))

        assertNull(reducer.confirm(ingredientsSelected))
        assertEquals(
            CandidateReviewConfirmation(
                mainDishCandidateId = "dish-1",
                photoEvidenceComponentIds = listOf("component-2", "component-1"),
            ),
            reducer.confirm(confirmed),
        )
    }

    private fun dish(id: String, rank: Int): InferenceCandidate = InferenceCandidate(
        candidateId = id,
        label = id,
        rank = rank,
        confidence = Confidence(0.9, ConfidenceBand.HIGH),
        unresolvedAssumptions = emptyList(),
        provenance = InferenceProvenance(
            route = InferenceRoute.LOCAL,
            engineId = "fixture-engine",
            modelVersion = "fixture-model",
            calibrationVersion = "fixture-calibration",
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        ),
    )

    private fun component(id: String, candidateId: String): VisibleComponent = VisibleComponent(
        componentId = id,
        candidate = LocalizedCandidateIdentity(candidateId, candidateId, "en"),
        confidence = CalibratedConfidence(0.9, ConfidenceBand.HIGH),
        visibility = ComponentVisibility.VISIBLE,
        ambiguity = ComponentAmbiguity.NONE,
        mass = MassEvidence.Estimated(
            range = GramRange(100.0, 150.0),
            calibrationAssumptions = listOf(MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION),
        ),
        provenance = InferenceV2Provenance(
            route = InferenceRoute.LOCAL,
            engineId = "fixture-engine",
            modelVersion = "fixture-model",
            taxonomyVersion = "fixture-taxonomy",
            calibrationVersion = "fixture-calibration",
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        ),
    )
}
