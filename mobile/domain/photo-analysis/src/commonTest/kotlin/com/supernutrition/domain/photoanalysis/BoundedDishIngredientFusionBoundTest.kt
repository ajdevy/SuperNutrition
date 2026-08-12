package com.supernutrition.domain.photoanalysis

import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.InferenceCandidate
import com.supernutrition.domain.inference.InferenceProvenance
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.PHOTO_INFERENCE_CONTRACT_V1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The bound this type is named for.
 *
 * It was not applying one. `merge` passed the engine's candidate list straight
 * into [CandidateReviewDraft], which admits at most three, so any engine
 * returning more threw `IllegalArgumentException` inside the analysis
 * coordinator — caught there as a generic failure and shown to the user as
 * "Photo capture failed" after a perfectly good HTTP 200.
 *
 * It survived Slice A because the backend stub returned exactly two candidates.
 * The real model returns up to five: the gateway prompt permits it, and four is
 * what the first real photo produced. Anything that fixes only "four" would miss
 * five, so these assertions run to the contract's ceiling.
 */
class BoundedDishIngredientFusionBoundTest {

    private fun candidate(rank: Int, score: Double = 0.9) = InferenceCandidate(
        candidateId = "food:candidate-$rank",
        label = "Candidate $rank",
        rank = rank,
        confidence = Confidence(score, ConfidenceBand.fromScore(score)),
        unresolvedAssumptions = emptyList(),
        provenance = InferenceProvenance(
            route = InferenceRoute.REMOTE,
            engineId = "gateway-remote",
            modelVersion = "m",
            calibrationVersion = "c",
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        ),
    )

    @Test
    fun mergeBoundsAnyEngineResultToWhatTheReviewAdmits() {
        // The gateway contract permits up to five candidates; the review admits
        // three. Every count the engine may legally return must merge.
        (1..5).forEach { count ->
            val draft = BoundedDishIngredientFusion.merge(
                dishes = (1..count).map { candidate(it) },
                photoEvidence = emptyList(),
            )

            assertTrue(
                draft.mainDishProposals.size <= CandidateReviewDraft.MAX_MAIN_DISH_PROPOSALS,
                "count=$count produced ${draft.mainDishProposals.size} proposals",
            )
            assertEquals(minOf(count, CandidateReviewDraft.MAX_MAIN_DISH_PROPOSALS), draft.mainDishProposals.size)
        }
    }

    @Test
    fun theBestRankedProposalsAreTheOnesKept() {
        // Truncating the tail is only correct if the list is ordered first. An
        // engine that returned its candidates out of order would otherwise have
        // its best proposal dropped.
        val draft = BoundedDishIngredientFusion.merge(
            dishes = listOf(candidate(4), candidate(2), candidate(5), candidate(1), candidate(3)),
            photoEvidence = emptyList(),
        )

        assertEquals(listOf(1, 2, 3), draft.mainDishProposals.map { it.rank })
    }

    @Test
    fun aFourCandidateResultMergesRatherThanThrowing() {
        // The exact shape that failed on device: four real-model candidates,
        // HTTP 200, and a draft that refused to be built.
        val draft = BoundedDishIngredientFusion.merge(
            dishes = (1..4).map { candidate(it) },
            photoEvidence = emptyList(),
        )

        assertEquals(3, draft.mainDishProposals.size)
        assertEquals("food:candidate-1", draft.mainDishProposals.first().candidateId)
    }

    // --- composition hints ---------------------------------------------------

    private fun dish(name: String, rank: Int, score: Double) = InferenceCandidate(
        candidateId = "food:$name",
        label = name,
        rank = rank,
        confidence = Confidence(score, ConfidenceBand.fromScore(score)),
        unresolvedAssumptions = emptyList(),
        provenance = InferenceProvenance(
            route = InferenceRoute.REMOTE,
            engineId = "gateway-remote",
            modelVersion = "m",
            calibrationVersion = "c",
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        ),
    )

    @Test
    fun onlyTheTopThreeDishesContributeHints() {
        // hintsFor bounds contributors to three. A fourth dish's ingredients must
        // not appear, or a low-ranked guess would put words under the photo.
        val draft = BoundedDishIngredientFusion.merge(
            dishes = listOf(
                dish("greek_salad", 1, 0.9),
                dish("ramen", 2, 0.8),
                dish("pizza", 3, 0.7),
                dish("waffles", 4, 0.6),
            ),
            photoEvidence = emptyList(),
        )

        val ids = draft.compositionHints.map { it.ingredientId }.toSet()
        assertTrue("ingredient:cucumber" in ids, "greek_salad should contribute: $ids")

        // Ingredients carried ONLY by the fourth dish. Naming ingredients the
        // table does not hold would make this assertion pass no matter what the
        // bound is — which is exactly what a first draft of it did.
        val fourthDishOnly = setOf(
            "ingredient:berries",
            "ingredient:butter",
            "ingredient:flour",
            "ingredient:milk",
            "ingredient:syrup",
        )
        assertTrue(fourthDishOnly.none { it in ids }, "a fourth dish contributed hints: $ids")
    }

    @Test
    fun hintsArriveBestSupportedFirstAndNormalisedToTheStrongest() {
        // Support is relative: the review shows how strongly each hint is implied,
        // so the strongest must normalise to 1.0 and the list must descend. The
        // draft enforces the ordering, so a regression here fails loudly rather
        // than reordering the user's list silently.
        val draft = BoundedDishIngredientFusion.merge(
            dishes = listOf(
                dish("greek_salad", 1, 0.9),
                dish("garden_salad", 2, 0.8),
                dish("pizza", 3, 0.2),
            ),
            photoEvidence = emptyList(),
        )

        val supports = draft.compositionHints.map { it.support }
        assertEquals(supports.sortedDescending(), supports, "hints must descend by support")
        assertEquals(1.0, supports.first(), "the strongest hint normalises to 1.0")
        assertTrue(supports.all { it in 0.0..1.0 }, "support is a normalised fraction: $supports")

        // tomato and cucumber are in BOTH salads, so they must outrank a hint
        // carried by one dish alone.
        val byId = draft.compositionHints.associate { it.ingredientId to it.support }
        val shared = byId.getValue("ingredient:tomato")
        val single = byId.getValue("ingredient:feta_cheese")
        assertTrue(shared > single, "shared=$shared single=$single")
    }

    @Test
    fun theHintListNeverExceedsItsCeiling() {
        // MAX_HINTS is 24 and the three-dish bound plus the prior table can only
        // reach 20, so this ceiling is not currently reachable — measured, not
        // assumed. It is asserted anyway: the moment the table grows or the
        // three-dish bound is raised, truncation becomes live, and the ordering
        // above then decides WHICH hints a user sees.
        val everyDish = listOf("greek_salad", "ramen", "pizza", "hamburger", "sushi", "bibimbap")
            .mapIndexed { index, name -> dish(name, index + 1, 0.9) }

        val hints = BoundedDishIngredientFusion.merge(everyDish, emptyList()).compositionHints

        assertTrue(hints.size <= 24, "hint ceiling exceeded: ${hints.size}")
        assertEquals(hints.map { it.ingredientId }.distinct().size, hints.size, "hint ids must be unique")
    }
}
