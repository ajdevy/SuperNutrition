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
import kotlin.test.assertTrue

class BoundedDishIngredientFusionTest {
    @Test
    fun `fusion limits dish proposals to the review contract`() {
        val dishes = (1..5).map { rank ->
            dish("dish-$rank", "Dish $rank", rank, 0.9 - rank * 0.1)
        }

        val draft = BoundedDishIngredientFusion.merge(dishes, emptyList())

        assertEquals(dishes.take(3), draft.mainDishProposals)
        assertTrue(draft.compositionHints.size <= 24)
    }

    @Test
    fun `bounded fusion preserves every visual component and keeps dish order`() {
        val dishes = listOf(
            dish("greek_salad", "Greek salad", 1, 0.9),
            dish("caesar_salad", "Caesar salad", 2, 0.6),
        )
        val visual = listOf(component("visual-broccoli", "ingredient:broccoli", "Broccoli"))

        val draft = BoundedDishIngredientFusion.merge(dishes, visual)

        assertEquals(dishes, draft.mainDishProposals)
        assertEquals(visual, draft.photoEvidence)
        assertEquals("e0-suggestion-policy.v1", BoundedDishIngredientFusion.POLICY_VERSION)
        assertTrue(draft.compositionHints.isNotEmpty())
        assertEquals(
            listOf("ingredient:cucumber", "ingredient:tomato"),
            draft.compositionHints.take(2).map { it.ingredientId },
        )
        assertTrue(draft.compositionHints.none { it.ingredientId == "ingredient:broccoli" })
    }

    @Test
    fun `same ingredient from multiple dishes is merged with deterministic support and sources`() {
        val draft = BoundedDishIngredientFusion.merge(
            dishes = listOf(
                dish("greek_salad", "Greek salad", 1, 0.8),
                dish("garden_salad", "Garden salad", 2, 0.7),
            ),
            photoEvidence = emptyList(),
        )

        val cucumber = draft.compositionHints.single { it.ingredientId == "ingredient:cucumber" }
        assertEquals(listOf("greek_salad", "garden_salad"), cucumber.sourceDishCandidateIds)
        assertEquals(1.0, cucumber.support)
        assertTrue(draft.compositionHints.size <= 24)
    }

    @Test
    fun `unknown dishes are a successful no-hint result`() {
        val dishes = listOf(dish("unknown-dish", "Unknown dish", 1, 0.9))
        val visual = listOf(component("visual-rice", "ingredient:rice", "Rice"))

        val draft = BoundedDishIngredientFusion.merge(dishes, visual)

        assertEquals(emptyList(), draft.compositionHints)
        assertEquals(visual, draft.photoEvidence)
    }

    @Test
    fun `label fallback and zero confidence remain bounded`() {
        val draft = BoundedDishIngredientFusion.merge(
            dishes = listOf(dish("unmapped", " Greek Salad ", 1, 0.0)),
            photoEvidence = emptyList(),
        )

        assertTrue(draft.compositionHints.isNotEmpty())
        assertTrue(draft.compositionHints.all { it.support == 0.0 })
    }

    @Test
    fun `hint invariants reject malformed values`() {
        assertFailsWith<IllegalArgumentException> {
            DishIngredientHint("food:tomato", "Tomato", 1.0, listOf("dish"))
        }
        assertFailsWith<IllegalArgumentException> {
            DishIngredientHint("ingredient:tomato", "", 1.0, listOf("dish"))
        }
        assertFailsWith<IllegalArgumentException> {
            DishIngredientHint("ingredient:tomato", "Tomato", 1.1, listOf("dish"))
        }
        assertFailsWith<IllegalArgumentException> {
            DishIngredientHint("ingredient:tomato", "Tomato", 1.0, emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            DishIngredientHint("ingredient:tomato", "Tomato", 1.0, listOf("dish", "dish"))
        }
    }

    private fun dish(id: String, label: String, rank: Int, score: Double) = InferenceCandidate(
        candidateId = id,
        label = label,
        rank = rank,
        confidence = Confidence(score, ConfidenceBand.fromScore(score)),
        unresolvedAssumptions = emptyList(),
        provenance = InferenceProvenance(
            route = InferenceRoute.LOCAL,
            engineId = "fixture-dish-engine",
            modelVersion = "fixture-dish-model",
            calibrationVersion = "fixture-dish-calibration",
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        ),
    )

    private fun component(id: String, candidateId: String, label: String) = VisibleComponent(
        componentId = id,
        candidate = LocalizedCandidateIdentity(candidateId, label, "en"),
        confidence = CalibratedConfidence(0.9, ConfidenceBand.HIGH),
        visibility = ComponentVisibility.VISIBLE,
        ambiguity = ComponentAmbiguity.NONE,
        mass = MassEvidence.Estimated(
            range = GramRange(100.0, 150.0),
            calibrationAssumptions = listOf(MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION),
        ),
        provenance = InferenceV2Provenance(
            route = InferenceRoute.LOCAL,
            engineId = "fixture-component-engine",
            modelVersion = "fixture-component-model",
            taxonomyVersion = "fixture-component-taxonomy",
            calibrationVersion = "fixture-component-calibration",
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        ),
    )
}
