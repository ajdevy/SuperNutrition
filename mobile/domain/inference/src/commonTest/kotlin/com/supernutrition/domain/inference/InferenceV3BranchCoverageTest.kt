@file:Suppress(
    "EqualsNullCall",
    "LargeClass",
    "LongMethod",
    "LongParameterList",
    "MaxLineLength",
    "MaximumLineLength",
)

package com.supernutrition.domain.inference

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Exercises the closed branches of the v3 binding. These are deliberately
 * contract cases rather than implementation-only tests: each branch is a
 * modality, outcome, evidence state, or telemetry bucket a producer can emit.
 */
class InferenceV3BranchCoverageTest {
    private val attempt = AttemptId("attempt-v3-coverage")
    private val context = InferenceAttemptContext(
        attemptId = attempt,
        requestId = "request-v3-coverage",
        configurationVersion = "config-1",
        configurationDigest = "a".repeat(64),
    )
    private val textProvenance = provenance(InferenceRouteV3.LOCAL_TEXT)
    private val photoProvenance = provenance(InferenceRouteV3.LOCAL)

    @Test
    fun text_request_equality_hash_and_redaction_cover_all_comparisons() {
        val first = InferenceRequestV3.text("eggs", "en-US", context)
        val same = InferenceRequestV3.Text("eggs", "en-US", context)
        val differentDescription = InferenceRequestV3.Text("toast", "en-US", context)
        val differentLocale = InferenceRequestV3.Text("eggs", "ru-RU", context)

        assertEquals(first, same)
        assertEquals(first.hashCode(), same.hashCode())
        assertFalse(first == differentDescription)
        assertFalse(first == differentLocale)
        assertFalse(first.equals(null))
        assertTrue("eggs" !in first.toString())
        assertFailsWith<IllegalArgumentException> {
            InferenceAttemptContext(
                attemptId = attempt,
                requestId = "request-v3-grid-negative",
                configurationVersion = "config-1",
                configurationDigest = "a".repeat(64),
                gridCellIndex = -1,
            )
        }
        assertEquals(
            0,
            InferenceAttemptContext(
                attemptId = attempt,
                requestId = "request-v3-grid-zero",
                configurationVersion = "config-1",
                configurationDigest = "a".repeat(64),
                gridCellIndex = 0,
            ).gridCellIndex,
        )
        assertEquals(
            8,
            InferenceAttemptContext(
                attemptId = attempt,
                requestId = "request-v3-grid-eight",
                configurationVersion = "config-1",
                configurationDigest = "a".repeat(64),
                gridCellIndex = 8,
            ).gridCellIndex,
        )
    }

    @Test
    fun candidate_groups_and_components_reject_empty_and_duplicate_collections() {
        assertFailsWith<IllegalArgumentException> {
            InferenceItemGroup("empty", InferenceSourceSpan(0, 1), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcomeV3.Candidates(emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcomeV3.Candidates(listOf(group("same"), group("same")))
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcomeV3.Components(emptyList())
        }
        val duplicateComponent = component("duplicate")
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcomeV3.Components(listOf(duplicateComponent, duplicateComponent))
        }
        assertFailsWith<IllegalArgumentException> {
            candidate(rank = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceCandidateV3(" ", "label", 1, Confidence(0.9, ConfidenceBand.HIGH), emptyList(), textProvenance)
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceCandidateV3(
                "food:valid",
                " ",
                1,
                Confidence(0.9, ConfidenceBand.HIGH),
                emptyList(),
                textProvenance,
            )
        }
    }

    @Test
    fun both_modalities_allow_typed_recovery_outcomes_but_not_each_others_success_shape() {
        val textAbstention = result(
            InferenceModality.TEXT,
            InferenceOutcomeV3.Abstention(
                reason = AbstentionReasonV3.DESCRIPTION_UNSEGMENTABLE,
                userMessageKey = "inference.abstention.description_unsegmentable",
                provenance = textProvenance,
            ),
        )
        val photoFailure = result(
            InferenceModality.PHOTO,
            InferenceOutcomeV3.Failure(
                code = InferenceFailureCode.TEMPORARY_UNAVAILABLE,
                retryable = true,
                userMessageKey = "inference.failure.temporary_unavailable",
                provenance = photoProvenance,
            ),
        )
        assertEquals(InferenceModality.TEXT, textAbstention.modality)
        assertEquals(InferenceModality.PHOTO, photoFailure.modality)
        assertFailsWith<IllegalArgumentException> {
            result(InferenceModality.TEXT, InferenceOutcomeV3.Components(listOf(component("photo-shape"))))
        }
        assertFailsWith<IllegalArgumentException> {
            result(InferenceModality.PHOTO, InferenceOutcomeV3.Candidates(listOf(group("text-shape"))))
        }
        assertFailsWith<IllegalArgumentException> {
            result(
                InferenceModality.PHOTO,
                InferenceOutcomeV3.Candidates(listOf(group("wrong-version"))),
                contractVersion = "inference-evidence/v2",
            )
        }
    }

    @Test
    fun photo_evidence_covers_regions_visibility_ambiguity_and_all_mass_states() {
        val estimated = InferenceV3MassEvidence.Estimated(
            range = InferenceV3GramRange(10.0, 20.0),
            calibrationAssumptions = listOf(
                InferenceV3MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION,
                InferenceV3MassCalibrationAssumption.REFERENCE_GEOMETRY_AVAILABLE,
                InferenceV3MassCalibrationAssumption.PARTIAL_OCCLUSION,
            ),
        )
        val partial = component(
            id = "estimated-partial",
            visibility = InferenceV3ComponentVisibility.PARTIALLY_OCCLUDED,
            ambiguity = InferenceV3ComponentAmbiguity.IDENTITY_AMBIGUOUS,
            region = InferenceV3NormalizedRegion(0.0, 0.0, 1.0, 1.0),
            mass = estimated,
        )
        assertIs<InferenceV3MassEvidence.Estimated>(partial.mass)
        assertIs<InferenceV3MassEvidence.NotEvaluable>(
            component(
                id = "not-evaluable",
                visibility = InferenceV3ComponentVisibility.PARTIALLY_OCCLUDED,
                ambiguity = InferenceV3ComponentAmbiguity.MIXED_CONTENT,
                mass = InferenceV3MassEvidence.NotEvaluable(
                    InferenceV3MassNotEvaluableReason.TEXT_HAS_NO_VISIBLE_PORTION,
                    "inference.mass.not_evaluable",
                ),
            ).mass,
        )
        assertIs<InferenceV3MassEvidence.Abstained>(
            component(
                id = "abstained",
                ambiguity = InferenceV3ComponentAmbiguity.IDENTITY_AMBIGUOUS,
                mass = InferenceV3MassEvidence.Abstained(
                    InferenceV3MassAbstentionReason.IMAGE_QUALITY,
                    "inference.mass.image_quality",
                ),
            ).mass,
        )
        assertFailsWith<IllegalArgumentException> {
            component(
                id = "estimated-without-region",
                visibility = InferenceV3ComponentVisibility.PARTIALLY_OCCLUDED,
                mass = estimated,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV3MassEvidence.Estimated(InferenceV3GramRange(1.0, 2.0), emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV3MassEvidence.Estimated(
                InferenceV3GramRange(1.0, 2.0),
                listOf(
                    InferenceV3MassCalibrationAssumption.PARTIAL_OCCLUSION,
                    InferenceV3MassCalibrationAssumption.PARTIAL_OCCLUSION,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV3MassEvidence.Abstained(
                InferenceV3MassAbstentionReason.IDENTITY_AMBIGUOUS,
                " ",
            )
        }
        val visibleEstimated = component(
            id = "estimated-visible",
            mass = estimated,
        )
        assertEquals(visibleEstimated, visibleEstimated.copy())
        assertFalse(visibleEstimated == component("different-visible"))
        assertFalse(visibleEstimated.equals(null))
        assertFalse(visibleEstimated.equals("not-a-component"))
        assertFalse(
            visibleEstimated == visibleEstimated.copy(
                candidate = InferenceV3LocalizedCandidate("ingredient:other", "Food", "en-US"),
            ),
        )
        assertFalse(visibleEstimated == visibleEstimated.copy(confidence = Confidence(0.7, ConfidenceBand.MEDIUM)))
        assertFalse(
            visibleEstimated == visibleEstimated.copy(
                visibility = InferenceV3ComponentVisibility.PARTIALLY_OCCLUDED,
                region = InferenceV3NormalizedRegion(0.1, 0.1, 0.2, 0.2),
            ),
        )
        assertFalse(visibleEstimated == visibleEstimated.copy(ambiguity = InferenceV3ComponentAmbiguity.MIXED_CONTENT))
        assertFalse(visibleEstimated == visibleEstimated.copy(region = InferenceV3NormalizedRegion(0.1, 0.1, 0.2, 0.2)))
        assertFalse(
            visibleEstimated == visibleEstimated.copy(
                mass = InferenceV3MassEvidence.NotEvaluable(
                    InferenceV3MassNotEvaluableReason.MISSING_CALIBRATION,
                    "inference.mass.missing_calibration",
                ),
            ),
        )
        assertFalse(visibleEstimated == visibleEstimated.copy(provenance = provenance(InferenceRouteV3.REMOTE)))

        val localized = InferenceV3LocalizedCandidate("ingredient:equality", "Food", "en-US")
        assertEquals(localized, localized.copy())
        assertFalse(localized == localized.copy(label = "Other"))
        assertFalse(localized.equals(null))
        assertFalse(localized.equals("not-a-candidate"))
        assertFalse(localized == localized.copy(candidateId = "ingredient:other"))
        assertFalse(localized == localized.copy(locale = "ru-RU"))
        val region = InferenceV3NormalizedRegion(0.1, 0.2, 0.3, 0.4)
        assertEquals(region, region.copy())
        assertFalse(region == region.copy(width = 0.2))
        assertFalse(region.equals(null))
        assertFalse(region.equals("not-a-region"))
        assertFalse(region == region.copy(left = 0.2))
        assertFalse(region == region.copy(top = 0.3))
        assertFalse(region == region.copy(height = 0.5))
        val range = InferenceV3GramRange(10.0, 20.0)
        assertEquals(range, range.copy())
        assertFalse(range == range.copy(upperGrams = 30.0))
        assertFalse(range.equals(null))
        assertFalse(range.equals("not-a-range"))
        assertFalse(range == range.copy(lowerGrams = 11.0))
        val notEvaluable = InferenceV3MassEvidence.NotEvaluable(
            InferenceV3MassNotEvaluableReason.MIXED_OR_OCCLUDED,
            "inference.mass.mixed_or_occluded",
        )
        assertEquals(notEvaluable, notEvaluable.copy())
        assertFalse(notEvaluable == notEvaluable.copy(userMessageKey = "other"))
        assertFalse(notEvaluable.equals(null))
        assertFalse(notEvaluable.equals("not-mass"))
        assertFalse(notEvaluable == notEvaluable.copy(reason = InferenceV3MassNotEvaluableReason.AMBIGUOUS_PORTION))
        val abstained = InferenceV3MassEvidence.Abstained(
            InferenceV3MassAbstentionReason.IMAGE_QUALITY,
            "inference.mass.image_quality",
        )
        assertEquals(abstained, abstained.copy())
        assertFalse(abstained == abstained.copy(userMessageKey = "other"))
        assertFalse(abstained.equals(null))
        assertFalse(abstained.equals("not-mass"))
        assertFalse(abstained == abstained.copy(reason = InferenceV3MassAbstentionReason.UNSUPPORTED_CONTENT))
        assertEquals(estimated, estimated.copy())
        assertFalse(estimated == estimated.copy(range = InferenceV3GramRange(11.0, 21.0)))
        assertFalse(estimated.equals(null))
        assertFalse(estimated.equals("not-mass"))
        assertFalse(
            estimated == estimated.copy(
                calibrationAssumptions = listOf(InferenceV3MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION),
            ),
        )
    }

    @Test
    fun evidence_bounds_and_identifiers_fail_closed() {
        assertFailsWith<IllegalArgumentException> {
            component(" ")
        }
        assertFailsWith<IllegalArgumentException> { InferenceV3LocalizedCandidate(" ", "label", "en-US") }
        assertFailsWith<IllegalArgumentException> { InferenceV3LocalizedCandidate("food:1", " ", "en-US") }
        assertFailsWith<IllegalArgumentException> { InferenceV3LocalizedCandidate("food:1", "label", "en_US") }
        assertFailsWith<IllegalArgumentException> { InferenceV3NormalizedRegion(Double.NaN, 0.0, 1.0, 1.0) }
        assertFailsWith<IllegalArgumentException> {
            InferenceV3NormalizedRegion(
                0.0,
                Double.POSITIVE_INFINITY,
                1.0,
                1.0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV3NormalizedRegion(
                0.0,
                0.0,
                Double.POSITIVE_INFINITY,
                1.0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV3NormalizedRegion(
                0.0,
                0.0,
                1.0,
                Double.POSITIVE_INFINITY,
            )
        }
        assertFailsWith<IllegalArgumentException> { InferenceV3NormalizedRegion(-0.1, 0.0, 0.1, 0.1) }
        assertFailsWith<IllegalArgumentException> { InferenceV3NormalizedRegion(1.1, 0.0, 0.1, 0.1) }
        assertFailsWith<IllegalArgumentException> { InferenceV3NormalizedRegion(0.0, -0.1, 0.1, 0.1) }
        assertFailsWith<IllegalArgumentException> { InferenceV3NormalizedRegion(0.0, 1.1, 0.1, 0.1) }
        assertFailsWith<IllegalArgumentException> { InferenceV3NormalizedRegion(0.0, 0.0, 0.0, 0.1) }
        assertFailsWith<IllegalArgumentException> { InferenceV3NormalizedRegion(0.0, 0.0, 1.1, 0.1) }
        assertFailsWith<IllegalArgumentException> { InferenceV3NormalizedRegion(0.0, 0.0, 0.1, 0.0) }
        assertFailsWith<IllegalArgumentException> { InferenceV3NormalizedRegion(0.0, 0.0, 0.1, 1.1) }
        assertFailsWith<IllegalArgumentException> { InferenceV3NormalizedRegion(0.9, 0.0, 0.2, 0.1) }
        assertFailsWith<IllegalArgumentException> { InferenceV3NormalizedRegion(0.0, 0.9, 0.1, 0.2) }
        assertFailsWith<IllegalArgumentException> { InferenceV3GramRange(Double.NaN, 1.0) }
        assertFailsWith<IllegalArgumentException> { InferenceV3GramRange(1.0, Double.NaN) }
        assertFailsWith<IllegalArgumentException> { InferenceV3GramRange(Double.POSITIVE_INFINITY, 2.0) }
        assertFailsWith<IllegalArgumentException> { InferenceV3GramRange(Double.NEGATIVE_INFINITY, 2.0) }
        assertFailsWith<IllegalArgumentException> { InferenceV3GramRange(1.0, Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { InferenceV3GramRange(1.0, Double.NEGATIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { InferenceV3GramRange(-1.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { InferenceV3GramRange(1.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { InferenceV3GramRange(2.0, 1.0) }
        assertFailsWith<IllegalArgumentException> {
            InferenceV3MassEvidence.NotEvaluable(
                InferenceV3MassNotEvaluableReason.AMBIGUOUS_PORTION,
                " ",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV3MassEvidence.Abstained(
                InferenceV3MassAbstentionReason.UNSUPPORTED_CONTENT,
                " ",
            )
        }
    }

    @Test
    fun result_value_objects_cover_equality_and_invalid_spans() {
        val span = InferenceSourceSpan(1, 4)
        assertEquals(span, span.copy())
        assertFalse(span == InferenceSourceSpan(1, 5))
        assertFalse(span.equals(null))
        assertFalse(span.equals("not-a-span"))
        assertFailsWith<IllegalArgumentException> { InferenceSourceSpan(-1, 1) }
        assertFailsWith<IllegalArgumentException> { InferenceSourceSpan(1, 1) }

        val candidate = candidate()
        assertEquals(candidate, candidate.copy())
        assertFalse(candidate == candidate.copy(label = "Other"))
        assertFalse(candidate.equals(null))
        assertFalse(candidate.equals("not-a-candidate"))
        assertFalse(candidate == candidate.copy(candidateId = "food:other"))
        assertFalse(candidate == candidate.copy(rank = 2))
        assertFalse(candidate == candidate.copy(confidence = Confidence(0.7, ConfidenceBand.MEDIUM)))
        assertFalse(
            candidate == candidate.copy(
                unresolvedAssumptions = listOf(
                    InferenceAssumption(AssumptionCode.PORTION_UNKNOWN, "inference.assumption.portion_unknown"),
                ),
            ),
        )
        assertFalse(candidate == candidate.copy(provenance = provenance(InferenceRouteV3.REMOTE)))
        val group = group("equality")
        assertEquals(group, group.copy())
        assertFalse(group == group.copy(groupId = "other-equality"))
        assertFalse(group.equals(null))
        assertFalse(group.equals("not-a-group"))
        assertFalse(group == group.copy(sourceSpan = InferenceSourceSpan(1, 3)))
        assertFalse(group == group.copy(candidates = listOf(candidate(rank = 1), candidate(rank = 2))))

        val provenance = textProvenance
        assertEquals(provenance, provenance.copy())
        assertFalse(provenance == provenance.copy(engineId = "other-engine"))
        assertFalse(provenance.equals(null))
        assertFalse(provenance.equals("not-provenance"))
        assertFalse(provenance == provenance.copy(route = InferenceRouteV3.REMOTE))
        assertFalse(provenance == provenance.copy(modelVersion = "other-model"))
        assertFalse(provenance == provenance.copy(taxonomyVersion = "other-taxonomy"))
        assertFalse(provenance == provenance.copy(calibrationVersion = "other-calibration"))

        val abstention = InferenceOutcomeV3.Abstention(
            AbstentionReasonV3.IMAGE_QUALITY,
            "inference.abstention.image_quality",
            photoProvenance,
        )
        val failure = InferenceOutcomeV3.Failure(
            InferenceFailureCode.MODEL_UNAVAILABLE,
            retryable = false,
            userMessageKey = "inference.failure.model_unavailable",
            provenance = photoProvenance,
        )
        assertEquals(abstention, abstention.copy())
        assertFalse(abstention == abstention.copy(userMessageKey = "other"))
        assertFalse(abstention.equals(null))
        assertFalse(abstention.equals("not-an-abstention"))
        assertFalse(abstention == abstention.copy(reason = AbstentionReasonV3.NO_FOOD_DETECTED))
        assertFalse(abstention == abstention.copy(provenance = provenance(InferenceRouteV3.REMOTE)))
        assertEquals(failure, failure.copy())
        assertFalse(failure == failure.copy(retryable = true))
        assertFalse(failure.equals(null))
        assertFalse(failure.equals("not-a-failure"))
        assertFalse(failure == failure.copy(code = InferenceFailureCode.INVALID_INPUT))
        assertFalse(failure == failure.copy(userMessageKey = "other"))
        assertFalse(failure == failure.copy(provenance = provenance(InferenceRouteV3.REMOTE)))
        val result = result(InferenceModality.PHOTO, abstention)
        assertEquals(result, result.copy())
        assertFalse(result == result.copy(modality = InferenceModality.TEXT))
        assertFalse(result.equals(null))
        assertFalse(result.equals("not-a-result"))
        assertFalse(result == result.copy(outcome = failure))

        assertFailsWith<IllegalArgumentException> {
            result(
                InferenceModality.TEXT,
                InferenceOutcomeV3.Candidates(listOf(group.copy(sourceSpan = null))),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceProvenanceV3(
                InferenceRouteV3.LOCAL_TEXT,
                " ",
                "model",
                "taxonomy",
                "calibration",
                INFERENCE_CONTRACT_V3,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceProvenanceV3(
                InferenceRouteV3.LOCAL_TEXT,
                "engine",
                " ",
                "taxonomy",
                "calibration",
                INFERENCE_CONTRACT_V3,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceProvenanceV3(
                InferenceRouteV3.LOCAL_TEXT,
                "engine",
                "model",
                " ",
                "calibration",
                INFERENCE_CONTRACT_V3,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceProvenanceV3(
                InferenceRouteV3.LOCAL_TEXT,
                "engine",
                "model",
                "taxonomy",
                " ",
                INFERENCE_CONTRACT_V3,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcomeV3.Abstention(
                AbstentionReasonV3.IMAGE_QUALITY,
                " ",
                photoProvenance,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcomeV3.Failure(
                InferenceFailureCode.INVALID_INPUT,
                retryable = false,
                userMessageKey = " ",
                provenance = photoProvenance,
            )
        }
        assertEquals(
            InferenceModality.PHOTO,
            InferenceResultV3(
                INFERENCE_CONTRACT_V3,
                InferenceModality.PHOTO,
                InferenceOutcomeV3.Abstention(
                    AbstentionReasonV3.IMAGE_QUALITY,
                    "inference.abstention.image_quality",
                    photoProvenance,
                ),
            ).modality,
        )
        assertEquals(
            InferenceModality.TEXT,
            InferenceResultV3(
                INFERENCE_CONTRACT_V3,
                InferenceModality.TEXT,
                InferenceOutcomeV3.Failure(
                    InferenceFailureCode.INVALID_INPUT,
                    retryable = false,
                    userMessageKey = "inference.failure.invalid_input",
                    provenance = textProvenance,
                ),
            ).modality,
        )
    }

    @Test
    fun telemetry_derives_every_terminal_outcome_and_bucket_boundary() {
        val candidateEvent = InferenceTelemetryEventV3.forAttempt(
            result(InferenceModality.TEXT, InferenceOutcomeV3.Candidates(listOf(group("candidate-event")))),
            CapabilityState.READY,
            TelemetryDurationBucketV3.UNDER_2S,
            TelemetryResourceBucketV3.LOW,
        )
        val componentEvent = InferenceTelemetryEventV3.forAttempt(
            result(InferenceModality.PHOTO, InferenceOutcomeV3.Components(listOf(component("component-event")))),
            CapabilityState.READY,
            TelemetryDurationBucketV3.FROM_2_TO_5S,
            TelemetryResourceBucketV3.MODERATE,
        )
        val abstentionEvent = InferenceTelemetryEventV3.forAttempt(
            result(
                InferenceModality.PHOTO,
                InferenceOutcomeV3.Abstention(
                    AbstentionReasonV3.IMAGE_QUALITY,
                    "inference.abstention.image_quality",
                    photoProvenance,
                ),
            ),
            CapabilityState.TEMPORARILY_UNAVAILABLE,
            TelemetryDurationBucketV3.OVER_5S,
            TelemetryResourceBucketV3.HIGH,
        )
        val failureEvent = InferenceTelemetryEventV3.forAttempt(
            result(
                InferenceModality.TEXT,
                InferenceOutcomeV3.Failure(
                    InferenceFailureCode.MODEL_UNAVAILABLE,
                    retryable = false,
                    userMessageKey = "inference.failure.model_unavailable",
                    provenance = textProvenance,
                ),
            ),
            CapabilityState.MODEL_REQUIRED,
            TelemetryDurationBucketV3.UNDER_2S,
            TelemetryResourceBucketV3.LOW,
        )
        assertEquals(InferenceV3TelemetryOutcome.CANDIDATES, candidateEvent.terminalOutcome)
        assertEquals(InferenceV3TelemetryOutcome.COMPONENTS, componentEvent.terminalOutcome)
        assertEquals(InferenceV3TelemetryOutcome.ABSTENTION, abstentionEvent.terminalOutcome)
        assertEquals(InferenceV3TelemetryOutcome.FAILURE, failureEvent.terminalOutcome)
        assertEquals(TelemetryItemGroupCountBucket.NONE, TelemetryItemGroupCountBucket.of(-1))
        assertEquals(TelemetryItemGroupCountBucket.NONE, TelemetryItemGroupCountBucket.of(0))
        assertEquals(TelemetryItemGroupCountBucket.ONE, TelemetryItemGroupCountBucket.of(1))
        assertEquals(TelemetryItemGroupCountBucket.TWO_TO_THREE, TelemetryItemGroupCountBucket.of(2))
        assertEquals(TelemetryItemGroupCountBucket.TWO_TO_THREE, TelemetryItemGroupCountBucket.of(3))
        assertEquals(TelemetryItemGroupCountBucket.FOUR_PLUS, TelemetryItemGroupCountBucket.of(4))
        assertEquals(TelemetryComponentCountBucket.ZERO, TelemetryComponentCountBucket.of(0))
        assertEquals(TelemetryComponentCountBucket.ONE, TelemetryComponentCountBucket.of(1))
        assertEquals(TelemetryComponentCountBucket.TWO_TO_FOUR, TelemetryComponentCountBucket.of(4))
        assertEquals(TelemetryComponentCountBucket.FIVE_OR_MORE, TelemetryComponentCountBucket.of(5))
        val photoRemote = InferenceTelemetryEventV3.forAttempt(
            result(
                InferenceModality.PHOTO,
                InferenceOutcomeV3.Failure(
                    InferenceFailureCode.INVALID_INPUT,
                    retryable = false,
                    userMessageKey = "inference.failure.invalid_input",
                    provenance = provenance(InferenceRouteV3.REMOTE),
                ),
            ),
            CapabilityState.UNSUPPORTED,
            TelemetryDurationBucketV3.UNDER_2S,
            TelemetryResourceBucketV3.LOW,
        )
        val textRemote = InferenceTelemetryEventV3.forAttempt(
            result(
                InferenceModality.TEXT,
                InferenceOutcomeV3.Abstention(
                    AbstentionReasonV3.DESCRIPTION_UNSEGMENTABLE,
                    "inference.abstention.description_unsegmentable",
                    provenance(InferenceRouteV3.REMOTE),
                ),
            ),
            CapabilityState.MODEL_REQUIRED,
            TelemetryDurationBucketV3.UNDER_2S,
            TelemetryResourceBucketV3.LOW,
        )
        assertEquals(InferenceRouteV3.REMOTE, photoRemote.route)
        assertEquals(InferenceRouteV3.REMOTE, textRemote.route)
    }

    @Test
    fun component_telemetry_rejects_mixed_provenance_and_blank_metadata() {
        val mixed = result(
            InferenceModality.PHOTO,
            InferenceOutcomeV3.Components(
                listOf(component("local", photoProvenance), component("remote", provenance(InferenceRouteV3.REMOTE))),
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            InferenceTelemetryEventV3.forAttempt(
                mixed,
                CapabilityState.READY,
                TelemetryDurationBucketV3.UNDER_2S,
                TelemetryResourceBucketV3.LOW,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceTelemetryEventV3(
                INFERENCE_CONTRACT_V3,
                InferenceModality.PHOTO,
                TelemetryEventNameV3.ATTEMPT_COMPLETED,
                InferenceRouteV3.LOCAL,
                CapabilityState.READY,
                TelemetryDurationBucketV3.UNDER_2S,
                TelemetryResourceBucketV3.LOW,
                InferenceV3TelemetryOutcome.COMPONENTS,
                TelemetryItemGroupCountBucket.NONE,
                TelemetryComponentCountBucket.ONE,
                "",
                "model",
                "taxonomy",
                "calibration",
            )
        }
        val blankValues = listOf(
            {
                InferenceTelemetryEventV3(
                    INFERENCE_CONTRACT_V3, InferenceModality.PHOTO, TelemetryEventNameV3.ATTEMPT_COMPLETED,
                    InferenceRouteV3.LOCAL, CapabilityState.READY, TelemetryDurationBucketV3.UNDER_2S,
                    TelemetryResourceBucketV3.LOW, InferenceV3TelemetryOutcome.COMPONENTS,
                    TelemetryItemGroupCountBucket.NONE, TelemetryComponentCountBucket.ONE,
                    "engine", " ", "taxonomy", "calibration",
                )
            },
            {
                InferenceTelemetryEventV3(
                    INFERENCE_CONTRACT_V3, InferenceModality.PHOTO, TelemetryEventNameV3.ATTEMPT_COMPLETED,
                    InferenceRouteV3.LOCAL, CapabilityState.READY, TelemetryDurationBucketV3.UNDER_2S,
                    TelemetryResourceBucketV3.LOW, InferenceV3TelemetryOutcome.COMPONENTS,
                    TelemetryItemGroupCountBucket.NONE, TelemetryComponentCountBucket.ONE,
                    "engine", "model", " ", "calibration",
                )
            },
            {
                InferenceTelemetryEventV3(
                    INFERENCE_CONTRACT_V3, InferenceModality.PHOTO, TelemetryEventNameV3.ATTEMPT_COMPLETED,
                    InferenceRouteV3.LOCAL, CapabilityState.READY, TelemetryDurationBucketV3.UNDER_2S,
                    TelemetryResourceBucketV3.LOW, InferenceV3TelemetryOutcome.COMPONENTS,
                    TelemetryItemGroupCountBucket.NONE, TelemetryComponentCountBucket.ONE,
                    "engine", "model", "taxonomy", " ",
                )
            },
        )
        blankValues.forEach { constructor -> assertFailsWith<IllegalArgumentException> { constructor() } }
        assertFailsWith<IllegalArgumentException> {
            InferenceTelemetryEventV3(
                INFERENCE_CONTRACT_V3,
                InferenceModality.TEXT,
                TelemetryEventNameV3.ATTEMPT_COMPLETED,
                InferenceRouteV3.LOCAL,
                CapabilityState.READY,
                TelemetryDurationBucketV3.UNDER_2S,
                TelemetryResourceBucketV3.LOW,
                InferenceV3TelemetryOutcome.CANDIDATES,
                TelemetryItemGroupCountBucket.ONE,
                TelemetryComponentCountBucket.ZERO,
                "engine",
                "model",
                "taxonomy",
                "calibration",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceTelemetryEventV3(
                INFERENCE_CONTRACT_V3,
                InferenceModality.PHOTO,
                TelemetryEventNameV3.ATTEMPT_COMPLETED,
                InferenceRouteV3.LOCAL_TEXT,
                CapabilityState.READY,
                TelemetryDurationBucketV3.UNDER_2S,
                TelemetryResourceBucketV3.LOW,
                InferenceV3TelemetryOutcome.COMPONENTS,
                TelemetryItemGroupCountBucket.NONE,
                TelemetryComponentCountBucket.ONE,
                "engine",
                "model",
                "taxonomy",
                "calibration",
            )
        }
        val event = InferenceTelemetryEventV3.forAttempt(
            result(InferenceModality.TEXT, InferenceOutcomeV3.Candidates(listOf(group("equality-event")))),
            CapabilityState.READY,
            TelemetryDurationBucketV3.UNDER_2S,
            TelemetryResourceBucketV3.LOW,
        )
        assertEquals(event, event.copy())
        assertFalse(event == event.copy(engineId = "other-engine"))
    }

    @Test
    fun shared_v1_models_keep_their_fail_closed_validation_covered() {
        val v1Provenance = InferenceProvenance(
            route = InferenceRoute.LOCAL,
            engineId = "engine-v1",
            modelVersion = "model-v1",
            calibrationVersion = "calibration-v1",
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        )
        val ready = EngineCapability(
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
            state = CapabilityState.READY,
            reason = CapabilityReason.NONE,
            userMessageKey = "inference.capability.ready",
            engineId = "engine-v1",
            modelVersion = "model-v1",
        )
        val unavailable = ready.copy(
            state = CapabilityState.MODEL_REQUIRED,
            reason = CapabilityReason.MODEL_MISSING,
            modelVersion = null,
        )
        assertEquals(CapabilityState.READY, ready.state)
        assertEquals(CapabilityState.MODEL_REQUIRED, unavailable.state)
        assertFailsWith<IllegalArgumentException> {
            ready.copy(reason = CapabilityReason.MODEL_MISSING)
        }
        assertFailsWith<IllegalArgumentException> {
            ready.copy(modelVersion = null)
        }
        assertFailsWith<IllegalArgumentException> {
            unavailable.copy(reason = CapabilityReason.NONE)
        }
        assertFailsWith<IllegalArgumentException> {
            EngineCapability(
                PHOTO_INFERENCE_CONTRACT_V1,
                CapabilityState.READY,
                CapabilityReason.NONE,
                " ",
                "engine-v1",
                "model-v1",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            PhotoInferenceResult(
                "inference-evidence/v2",
                InferenceOutcome.Abstention(
                    AbstentionReason.IMAGE_QUALITY,
                    "inference.abstention.image_quality",
                    v1Provenance,
                ),
            )
        }

        val low = Confidence(0.2, ConfidenceBand.LOW)
        val medium = Confidence(0.7, ConfidenceBand.MEDIUM)
        val high = Confidence(0.9, ConfidenceBand.HIGH)
        assertEquals(ConfidenceBand.LOW, low.band)
        assertEquals(ConfidenceBand.MEDIUM, medium.band)
        assertEquals(ConfidenceBand.HIGH, high.band)
        assertFailsWith<IllegalArgumentException> { Confidence(Double.NaN, ConfidenceBand.LOW) }
        assertFailsWith<IllegalArgumentException> { Confidence(-0.1, ConfidenceBand.LOW) }
        assertFailsWith<IllegalArgumentException> { Confidence(1.1, ConfidenceBand.HIGH) }
        assertFailsWith<IllegalArgumentException> { Confidence(0.2, ConfidenceBand.HIGH) }
        assertFailsWith<IllegalArgumentException> { InferenceAssumption(AssumptionCode.MIXED_DISH, " ") }

        fun v1Candidate(id: String, rank: Int) = InferenceCandidate(
            candidateId = id,
            label = id,
            rank = rank,
            confidence = low,
            unresolvedAssumptions = emptyList(),
            provenance = v1Provenance,
        )
        assertFailsWith<IllegalArgumentException> { InferenceOutcome.Candidates(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Candidates(listOf(v1Candidate("one", 1), v1Candidate("two", 1)))
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Candidates(listOf(v1Candidate("one", 1), v1Candidate("two", 3)))
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceOutcome.Candidates(listOf(v1Candidate("same", 1), v1Candidate("same", 2)))
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceCandidate(
                " ",
                "label",
                1,
                low,
                emptyList(),
                v1Provenance,
            )
        }
        assertFailsWith<IllegalArgumentException> { InferenceCandidate("id", " ", 1, low, emptyList(), v1Provenance) }
        assertFailsWith<IllegalArgumentException> {
            InferenceCandidate(
                "id",
                "label",
                0,
                low,
                emptyList(),
                v1Provenance,
            )
        }
    }

    @Test
    fun v3_recovery_and_mass_variants_round_trip_through_the_wire_codec() {
        val photoComponent = component(
            id = "wire-not-evaluable",
            mass = InferenceV3MassEvidence.NotEvaluable(
                InferenceV3MassNotEvaluableReason.UNSUPPORTED_GEOMETRY,
                "inference.mass.unsupported_geometry",
            ),
        )
        val photo = result(InferenceModality.PHOTO, InferenceOutcomeV3.Components(listOf(photoComponent)))
        assertEquals(photo, InferenceContractV3Json.decode(InferenceContractV3Json.encode(photo)))
        val notEvaluable = photoComponent.mass as InferenceV3MassEvidence.NotEvaluable
        val massJson = INFERENCE_JSON.encodeToString(InferenceV3MassEvidence.NotEvaluable.serializer(), notEvaluable)
        assertEquals(
            notEvaluable,
            INFERENCE_JSON.decodeFromString(InferenceV3MassEvidence.NotEvaluable.serializer(), massJson),
        )
        val estimatedWire = InferenceV3MassEvidence.Estimated(
            InferenceV3GramRange(10.0, 20.0),
            listOf(InferenceV3MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION),
        )
        val estimatedJson = INFERENCE_JSON.encodeToString(
            InferenceV3MassEvidence.Estimated.serializer(),
            estimatedWire,
        )
        assertFailsWith<IllegalArgumentException> {
            INFERENCE_JSON.decodeFromString(
                InferenceV3MassEvidence.Estimated.serializer(),
                estimatedJson.replace("[\"single-view-approximation\"]", "[]"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            INFERENCE_JSON.decodeFromString(
                InferenceV3MassEvidence.Estimated.serializer(),
                estimatedJson.replace(
                    "[\"single-view-approximation\"]",
                    "[\"single-view-approximation\",\"single-view-approximation\"]",
                ),
            )
        }
        val componentJson = INFERENCE_JSON.encodeToString(InferenceV3VisibleComponent.serializer(), photoComponent)
        assertEquals(
            photoComponent,
            INFERENCE_JSON.decodeFromString(InferenceV3VisibleComponent.serializer(), componentJson),
        )

        val abstention = result(
            InferenceModality.TEXT,
            InferenceOutcomeV3.Abstention(
                AbstentionReasonV3.DESCRIPTION_UNSEGMENTABLE,
                "inference.abstention.description_unsegmentable",
                textProvenance,
            ),
        )
        val failure = result(
            InferenceModality.PHOTO,
            InferenceOutcomeV3.Failure(
                InferenceFailureCode.RESOURCE_LIMIT,
                retryable = true,
                userMessageKey = "inference.failure.resource_limit",
                provenance = photoProvenance,
            ),
        )
        assertEquals(abstention, InferenceContractV3Json.decode(InferenceContractV3Json.encode(abstention)))
        assertEquals(failure, InferenceContractV3Json.decode(InferenceContractV3Json.encode(failure)))
    }

    @Test
    fun v3_serializers_reject_missing_required_fields() {
        val missing = listOf<() -> Any>(
            { INFERENCE_JSON.decodeFromString(ComponentModelManifest.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceV2Capability.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceV2Result.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceV2Outcome.Abstention.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceV2Outcome.Failure.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(VisibleComponent.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(LocalizedCandidateIdentity.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(CalibratedConfidence.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(NormalizedVisibleRegion.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(EngineCapabilityV3.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceV3VisibleComponent.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceV3LocalizedCandidate.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceV3GramRange.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceV3MassEvidence.Estimated.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceV3MassEvidence.NotEvaluable.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceV3MassEvidence.Abstained.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceProvenanceV3.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceCandidateV3.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceSourceSpan.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceItemGroup.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceOutcomeV3.Candidates.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceOutcomeV3.Components.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceOutcomeV3.Abstention.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceOutcomeV3.Failure.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceResultV3.serializer(), "{}") },
            { INFERENCE_JSON.decodeFromString(InferenceTelemetryEventV3.serializer(), "{}") },
        )
        missing.forEach { decoder -> assertFailsWith<SerializationException> { decoder() } }

        val regionComponent = component(
            id = "wire-region",
            region = InferenceV3NormalizedRegion(0.1, 0.2, 0.3, 0.4),
        )
        val validRegionJson = INFERENCE_JSON.encodeToString(InferenceV3VisibleComponent.serializer(), regionComponent)
        val missingRegionFields = validRegionJson.replace(
            "\"region\":{\"left\":0.1,\"top\":0.2,\"width\":0.3,\"height\":0.4}",
            "\"region\":{}",
        )
        assertFailsWith<SerializationException> {
            INFERENCE_JSON.decodeFromString(InferenceV3VisibleComponent.serializer(), missingRegionFields)
        }
        val componentObject = INFERENCE_JSON.parseToJsonElement(validRegionJson).jsonObject
        val regionObject = componentObject.getValue("region").jsonObject
        listOf("left", "top", "width", "height").forEach { field ->
            val regionWithoutField = JsonObject(regionObject.toMutableMap().apply { remove(field) })
            val componentWithoutRegionField = JsonObject(
                componentObject.toMutableMap().apply { put("region", regionWithoutField) },
            )
            assertTrue(
                runCatching {
                    INFERENCE_JSON.decodeFromString(
                        InferenceV3VisibleComponent.serializer(),
                        componentWithoutRegionField.toString(),
                    )
                }.isFailure,
                "Region must reject missing field $field",
            )
        }

        val productionManifest = ComponentModelManifest(
            manifestVersion = COMPONENT_MODEL_MANIFEST_V1,
            modelVersion = "component-model-2",
            artifactDigest = "a".repeat(64),
            taxonomyVersion = "taxonomy-2",
            taxonomyDigest = "b".repeat(64),
            preprocessingVersion = "preprocess-2",
            postprocessingVersion = "postprocess-2",
            maskContractVersion = "mask-1",
            calibrationVersion = "calibration-2",
            runtime = "runtime-1",
            executionProvider = "provider-1",
            compatibilityTier = "tier-1",
            ownership = ComponentModelOwnership.APP_MANAGED,
            activation = ComponentModelActivation.PRODUCTION,
            rollbackTargetVersion = "component-model-1",
            productApprovalRef = "approval:product-2",
            engineeringApprovalRef = "approval:engineering-2",
            legalApprovalRef = "approval:legal-2",
            corpusRef = "corpus:2",
            benchmarkEvidenceRef = "evidence:benchmark-2",
            rollbackEvidenceRef = "evidence:rollback-2",
        )
        val manifestObject = INFERENCE_JSON
            .parseToJsonElement(INFERENCE_JSON.encodeToString(ComponentModelManifest.serializer(), productionManifest))
            .jsonObject
        val requiredManifestFields = listOf(
            "manifestVersion", "modelVersion", "artifactDigest", "taxonomyVersion", "taxonomyDigest",
            "preprocessingVersion", "postprocessingVersion", "maskContractVersion", "calibrationVersion",
            "runtime", "executionProvider", "compatibilityTier", "ownership", "activation",
            "rollbackTargetVersion", "productApprovalRef", "engineeringApprovalRef", "legalApprovalRef",
            "corpusRef", "benchmarkEvidenceRef", "rollbackEvidenceRef",
        )
        requiredManifestFields.forEach { field ->
            val missingFieldObject = JsonObject(manifestObject.toMutableMap().apply { remove(field) })
            assertTrue(
                runCatching {
                    INFERENCE_JSON.decodeFromString(ComponentModelManifest.serializer(), missingFieldObject.toString())
                }.isFailure,
                "Manifest must reject missing field $field",
            )
        }
        val defaultsJson = Json {
            encodeDefaults = true
            explicitNulls = true
        }
        val stagedManifest = productionManifest.copy(
            activation = ComponentModelActivation.STAGED,
            rollbackTargetVersion = null,
            productApprovalRef = null,
            engineeringApprovalRef = null,
            legalApprovalRef = null,
            corpusRef = null,
            benchmarkEvidenceRef = null,
            rollbackEvidenceRef = null,
        )
        val stagedObject = defaultsJson
            .parseToJsonElement(defaultsJson.encodeToString(ComponentModelManifest.serializer(), stagedManifest))
            .jsonObject
        listOf("rollbackTargetVersion", "productApprovalRef", "engineeringApprovalRef").forEach { field ->
            val objectWithoutDefault = JsonObject(stagedObject.toMutableMap().apply { remove(field) })
            val decoded = defaultsJson.decodeFromString(
                ComponentModelManifest.serializer(),
                objectWithoutDefault.toString(),
            )
            assertEquals(null, objectWithoutDefault[field])
            assertEquals(stagedManifest, decoded)
        }
    }

    private fun provenance(route: InferenceRouteV3) = InferenceProvenanceV3(
        route = route,
        engineId = "engine-v3",
        modelVersion = "model-v3",
        taxonomyVersion = "taxonomy-v3",
        calibrationVersion = "calibration-v3",
        contractVersion = INFERENCE_CONTRACT_V3,
    )

    private fun candidate(rank: Int = 1) = InferenceCandidateV3(
        candidateId = "food:coverage-$rank",
        label = "Food $rank",
        rank = rank,
        confidence = Confidence(0.91, ConfidenceBand.HIGH),
        unresolvedAssumptions = emptyList(),
        provenance = textProvenance,
    )

    private fun group(id: String) = InferenceItemGroup(
        groupId = id,
        sourceSpan = InferenceSourceSpan(0, 1),
        candidates = listOf(candidate()),
    )

    private fun component(
        id: String,
        provenance: InferenceProvenanceV3 = photoProvenance,
        visibility: InferenceV3ComponentVisibility = InferenceV3ComponentVisibility.VISIBLE,
        ambiguity: InferenceV3ComponentAmbiguity = InferenceV3ComponentAmbiguity.NONE,
        region: InferenceV3NormalizedRegion? = null,
        mass: InferenceV3MassEvidence = InferenceV3MassEvidence.NotEvaluable(
            InferenceV3MassNotEvaluableReason.MIXED_OR_OCCLUDED,
            "inference.mass.mixed_or_occluded",
        ),
    ) = InferenceV3VisibleComponent(
        componentId = "component:$id",
        candidate = InferenceV3LocalizedCandidate("ingredient:$id", "Food", "en-US"),
        confidence = Confidence(0.91, ConfidenceBand.HIGH),
        visibility = visibility,
        ambiguity = ambiguity,
        region = region,
        mass = mass,
        provenance = provenance,
    )

    private fun result(
        modality: InferenceModality,
        outcome: InferenceOutcomeV3,
        contractVersion: String = INFERENCE_CONTRACT_V3,
    ) = InferenceResultV3(contractVersion, modality, outcome)
}
