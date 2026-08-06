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
}
