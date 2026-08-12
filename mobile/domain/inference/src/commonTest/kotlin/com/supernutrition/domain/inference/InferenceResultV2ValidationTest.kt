package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Every rejection the v2 result types promise.
 *
 * The happy paths are covered by decoding the canonical fixtures. These are the
 * `require` branches, which are the contract enforcement itself — an untested
 * rejection is a rejection nobody has established actually happens, and each one
 * here exists because the alternative reaches a user as a confident wrong
 * answer.
 */
class InferenceResultV2ValidationTest {
    private val provenance = InferenceProvenanceV2(
        route = InferenceRouteV2.LOCAL_TEXT,
        engineId = "engine-1",
        modelVersion = "model-1",
        calibrationVersion = "calibration-1",
        contractVersion = INFERENCE_CONTRACT_V2,
    )

    private fun candidate(id: String = "food:egg-boiled", rank: Int = 1) = InferenceCandidateV2(
        candidateId = id,
        label = "Egg, boiled",
        rank = rank,
        confidence = Confidence(0.91, ConfidenceBand.HIGH),
        unresolvedAssumptions = emptyList(),
        provenance = provenance,
    )

    @Test
    fun `a span must point at real characters`() {
        assertFailsWith<IllegalArgumentException> { InferenceSourceSpan(-1, 5) }
        assertFailsWith<IllegalArgumentException> { InferenceSourceSpan(5, 5) }
        assertFailsWith<IllegalArgumentException> { InferenceSourceSpan(6, 5) }
        assertEquals(0, InferenceSourceSpan(0, 1).start)
    }

    @Test
    fun `a candidate must be identifiable and ranked`() {
        assertFailsWith<IllegalArgumentException> { candidate(id = " ") }
        assertFailsWith<IllegalArgumentException> { candidate(rank = 0) }
        assertFailsWith<IllegalArgumentException> { candidate(rank = -1) }
        assertFailsWith<IllegalArgumentException> {
            InferenceCandidateV2("food:x", " ", 1, Confidence(0.9, ConfidenceBand.HIGH), emptyList(), provenance)
        }
    }

    @Test
    fun `provenance must name its engine and its contract`() {
        for (blank in listOf(1, 2, 3)) {
            assertFailsWith<IllegalArgumentException> {
                InferenceProvenanceV2(
                    route = InferenceRouteV2.LOCAL,
                    engineId = if (blank == 1) " " else "engine-1",
                    modelVersion = if (blank == 2) " " else "model-1",
                    calibrationVersion = if (blank == 3) " " else "calibration-1",
                    contractVersion = INFERENCE_CONTRACT_V2,
                )
            }
        }
        // A v1 version here would let a v1 result masquerade as v2.
        assertFailsWith<IllegalArgumentException> {
            provenance.copy(contractVersion = PHOTO_INFERENCE_CONTRACT_V1)
        }
    }

    @Test
    fun `an item group must be identifiable, non-empty, and unambiguously ranked`() {
        assertFailsWith<IllegalArgumentException> { InferenceItemGroup(" ", null, listOf(candidate())) }
        assertFailsWith<IllegalArgumentException> { InferenceItemGroup("item-1", null, emptyList()) }
        // Two candidates claiming rank 1 leaves no defined best answer.
        assertFailsWith<IllegalArgumentException> {
            InferenceItemGroup("item-1", null, listOf(candidate("food:a"), candidate("food:b")))
        }
        assertEquals(
            2,
            InferenceItemGroup("item-1", null, listOf(candidate("food:a"), candidate("food:b", 2))).candidates.size,
        )
    }

    @Test
    fun `a candidates outcome must carry distinct, non-empty groups`() {
        assertFailsWith<IllegalArgumentException> { InferenceOutcomeV2.Candidates(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcomeV2.Candidates(
                listOf(
                    InferenceItemGroup("item-1", null, listOf(candidate())),
                    InferenceItemGroup("item-1", null, listOf(candidate())),
                ),
            )
        }
    }

    @Test
    fun `abstention and failure must carry a message key the surface can show`() {
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcomeV2.Abstention(AbstentionReasonV2.DESCRIPTION_UNSEGMENTABLE, " ", provenance)
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcomeV2.Failure(InferenceFailureCode.TEMPORARY_UNAVAILABLE, true, " ", provenance)
        }
        val abstained = InferenceOutcomeV2.Abstention(
            AbstentionReasonV2.NO_FOOD_DETECTED,
            "inference.abstention.no_food",
            provenance,
        )
        assertEquals(AbstentionReasonV2.NO_FOOD_DETECTED, abstained.reason)
    }

    @Test
    fun `a non-candidate outcome is exempt from the segmentation rule`() {
        // Abstention and failure have no groups to anchor, so the span rules
        // must not reject them for either modality.
        for (modality in listOf(InferenceModality.TEXT, InferenceModality.PHOTO)) {
            val result = InferenceResultV2(
                contractVersion = INFERENCE_CONTRACT_V2,
                modality = modality,
                outcome = InferenceOutcomeV2.Abstention(
                    AbstentionReasonV2.IDENTITY_AMBIGUOUS,
                    "inference.abstention.identity_ambiguous",
                    provenance,
                ),
            )
            assertEquals(modality, result.modality)
        }
    }

    @Test
    fun `a result must declare the contract it speaks`() {
        assertFailsWith<IllegalArgumentException> {
            InferenceResultV2(
                contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
                modality = InferenceModality.TEXT,
                outcome = InferenceOutcomeV2.Candidates(
                    listOf(InferenceItemGroup("item-1", InferenceSourceSpan(0, 3), listOf(candidate()))),
                ),
            )
        }
    }

    @Test
    fun `a text request compares by value and never prints its description`() {
        val context = InferenceAttemptContext(
            attemptId = AttemptId("attempt-1"),
            requestId = "request-1",
            configurationVersion = "config-1",
            configurationDigest = "a".repeat(64),
        )
        val one = InferenceRequestV2.text("two boiled eggs", "en-US", context)
        val same = InferenceRequestV2.text("two boiled eggs", "en-US", context)
        val otherText = InferenceRequestV2.text("rye toast", "en-US", context)
        val otherLocale = InferenceRequestV2.text("two boiled eggs", "ru-RU", context)

        assertEquals(one, same)
        assertEquals(one.hashCode(), same.hashCode())
        assertNotEquals(one, otherText)
        assertNotEquals(one, otherLocale)
        assertNotEquals<Any?>(one, "two boiled eggs")
        // FM-5: a description can carry an address typed by mistake, so it must
        // not reach a log line through a request's toString.
        assertTrue("two boiled eggs" !in one.toString(), "the description must never be printed")
        assertTrue("redacted" in one.toString())
    }

    @Test
    fun `a blank text request is refused before any engine sees it`() {
        val context = InferenceAttemptContext(
            attemptId = AttemptId("attempt-1"),
            requestId = "request-1",
            configurationVersion = "config-1",
            configurationDigest = "a".repeat(64),
        )
        assertFailsWith<IllegalArgumentException> { InferenceRequestV2.text("   ", "en-US", context) }
        assertFailsWith<IllegalArgumentException> { InferenceRequestV2.text("eggs", " ", context) }
    }

    @Test
    fun `the band thresholds match the ones the contract publishes`() {
        // contracts/inference/v2/schema.toml [result.confidence] states
        // band_low_below = 0.5, band_medium_below = 0.8, band_high_from = 0.8.
        // The rule now lives in two places, so it needs something that fails
        // when they disagree — otherwise the contract documents one rule and
        // the binding enforces another, which is worse than the contract having
        // stayed silent.
        assertEquals(ConfidenceBand.LOW, ConfidenceBand.fromScore(0.0))
        assertEquals(ConfidenceBand.LOW, ConfidenceBand.fromScore(0.49))
        assertEquals(ConfidenceBand.MEDIUM, ConfidenceBand.fromScore(0.5))
        assertEquals(ConfidenceBand.MEDIUM, ConfidenceBand.fromScore(0.79))
        assertEquals(ConfidenceBand.HIGH, ConfidenceBand.fromScore(0.8))
        assertEquals(ConfidenceBand.HIGH, ConfidenceBand.fromScore(1.0))

        // band_mismatch = "reject": a band that disagrees with its score never
        // constructs, so a miscalibrated engine cannot show high confidence
        // over a low number.
        assertFailsWith<IllegalArgumentException> { Confidence(0.2, ConfidenceBand.HIGH) }
        assertFailsWith<IllegalArgumentException> { Confidence(0.9, ConfidenceBand.LOW) }

        // score_range = "0.0-1.0-inclusive"
        assertFailsWith<IllegalArgumentException> { Confidence(1.1, ConfidenceBand.HIGH) }
        assertFailsWith<IllegalArgumentException> { Confidence(-0.1, ConfidenceBand.LOW) }
    }
}

/**
 * A span has to point INTO the description it claims to quote.
 *
 * `InferenceSourceSpan` checks `start >= 0` and `end > start`, which are
 * properties of the span alone. Nothing bounded it against the text, because
 * the result does not carry the description — it lives on the request. So
 * `InferenceSourceSpan(0, 900)` on a nine-character description constructed
 * happily, and the provenance the user is shown ("these words produced this
 * item") would be read out of range at the first surface that tried to
 * highlight it.
 *
 * The check therefore belongs where request and result meet, which is what
 * `requireSpansWithin` is.
 */
class InferenceSourceSpanBoundsTest {
    private val provenance = InferenceProvenanceV2(
        route = InferenceRouteV2.LOCAL_TEXT,
        engineId = "engine-1",
        modelVersion = "model-1",
        calibrationVersion = "calibration-1",
        contractVersion = INFERENCE_CONTRACT_V2,
    )

    private fun textResult(vararg spans: InferenceSourceSpan) = InferenceResultV2(
        contractVersion = INFERENCE_CONTRACT_V2,
        modality = InferenceModality.TEXT,
        outcome = InferenceOutcomeV2.Candidates(
            itemGroups = spans.mapIndexed { index, span ->
                InferenceItemGroup(
                    groupId = "group-$index",
                    sourceSpan = span,
                    candidates = listOf(
                        InferenceCandidateV2(
                            candidateId = "food:egg-boiled",
                            label = "Egg, boiled",
                            rank = 1,
                            confidence = Confidence(0.9, ConfidenceBand.HIGH),
                            unresolvedAssumptions = emptyList(),
                            provenance = provenance,
                        ),
                    ),
                )
            },
        ),
    )

    @Test
    fun accepts_spans_that_lie_within_the_description() {
        val description = "two boiled eggs"
        textResult(InferenceSourceSpan(0, 3), InferenceSourceSpan(4, 15))
            .requireSpansWithin(description)
    }

    @Test
    fun accepts_a_span_ending_exactly_at_the_end() {
        textResult(InferenceSourceSpan(0, 9)).requireSpansWithin("some rice")
    }

    @Test
    fun rejects_a_span_running_past_the_end() {
        assertFailsWith<IllegalArgumentException> {
            textResult(InferenceSourceSpan(0, 900)).requireSpansWithin("some rice")
        }
    }

    @Test
    fun rejects_a_span_starting_past_the_end() {
        assertFailsWith<IllegalArgumentException> {
            textResult(InferenceSourceSpan(40, 50)).requireSpansWithin("some rice")
        }
    }

    // One anchored item and one that is not is the FM-6 failure with provenance
    // attached, so the whole result is refused rather than the bad group alone.
    @Test
    fun rejects_when_only_one_of_several_spans_overruns() {
        assertFailsWith<IllegalArgumentException> {
            textResult(InferenceSourceSpan(0, 4), InferenceSourceSpan(5, 99))
                .requireSpansWithin("some rice")
        }
    }

    // A photo result has no description to be bounded by, and carries no spans.
    @Test
    fun a_photo_result_is_unaffected() {
        InferenceResultV2(
            contractVersion = INFERENCE_CONTRACT_V2,
            modality = InferenceModality.PHOTO,
            outcome = InferenceOutcomeV2.Candidates(
                itemGroups = listOf(
                    InferenceItemGroup(
                        groupId = "group-0",
                        sourceSpan = null,
                        candidates = listOf(
                            InferenceCandidateV2(
                                candidateId = "food:egg-boiled",
                                label = "Egg, boiled",
                                rank = 1,
                                confidence = Confidence(0.9, ConfidenceBand.HIGH),
                                unresolvedAssumptions = emptyList(),
                                provenance = provenance,
                            ),
                        ),
                    ),
                ),
            ),
        ).requireSpansWithin("")
    }
}
