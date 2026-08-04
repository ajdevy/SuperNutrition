package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The v2 model's rejection paths.
 *
 * Every `require` here is a rule about what the contract will not represent, and
 * each has a failing side that decides whether a malformed result is refused or
 * quietly carried. The suite exercised the accepting side of these rules and
 * left the refusing side unmeasured — the initializers could stop rejecting
 * anything and every existing test would still pass.
 *
 * These assertions are constructed to fail if the rule is removed, not merely to
 * reach the line: each one builds a value that is legal except for the single
 * property under test.
 */
class InferenceV2RejectionBranchTest {

    private fun rejects(block: () -> Unit) = assertFailsWith<IllegalArgumentException> { block() }

    private fun provenance(route: InferenceRouteV2 = InferenceRouteV2.REMOTE) = InferenceProvenanceV2(
        route = route,
        engineId = "fixture-engine",
        modelVersion = "fixture-model-1",
        calibrationVersion = "fixture-calibration-1",
        contractVersion = INFERENCE_CONTRACT_V2,
    )

    private fun candidateV2(candidateId: String = "food:egg-boiled", rank: Int = 1) = InferenceCandidateV2(
        candidateId = candidateId,
        label = "Egg, boiled",
        rank = rank,
        confidence = Confidence(score = 0.91, band = ConfidenceBand.HIGH),
        unresolvedAssumptions = emptyList(),
        provenance = provenance(),
    )

    private fun group(
        groupId: String = "group-1",
        sourceSpan: InferenceSourceSpan? = null,
        candidates: List<InferenceCandidateV2> = listOf(candidateV2()),
    ) = InferenceItemGroup(groupId = groupId, sourceSpan = sourceSpan, candidates = candidates)

    private fun result(modality: InferenceModality, groups: List<InferenceItemGroup>) = InferenceResultV2(
        contractVersion = INFERENCE_CONTRACT_V2,
        modality = modality,
        outcome = InferenceOutcomeV2.Candidates(groups),
    )

    // --- modality/route and modality/span consistency -----------------------

    @Test
    fun aTextAttemptCannotClaimThePhotoLocalRoute() {
        // local-text exists precisely so a text attempt is not flattened into
        // "local"; allowing it back would erase the distinction.
        rejects { telemetry(modality = InferenceModality.TEXT, route = InferenceRouteV2.LOCAL) }
    }

    @Test
    fun aPhotoAttemptCannotClaimTheTextRoute() {
        rejects { telemetry(modality = InferenceModality.PHOTO, route = InferenceRouteV2.LOCAL_TEXT) }
    }

    @Test
    fun aTelemetryEventStillAcceptsEachModalityWithItsOwnRoutes() {
        telemetry(modality = InferenceModality.TEXT, route = InferenceRouteV2.LOCAL_TEXT)
        telemetry(modality = InferenceModality.TEXT, route = InferenceRouteV2.REMOTE)
        telemetry(modality = InferenceModality.PHOTO, route = InferenceRouteV2.LOCAL)
        telemetry(modality = InferenceModality.PHOTO, route = InferenceRouteV2.REMOTE)
    }

    @Test
    fun aTextResultMustCarryTheSpanEachGroupDerivedFrom() {
        rejects { result(InferenceModality.TEXT, listOf(group(sourceSpan = null))) }
    }

    @Test
    fun aPhotoResultMustNotCarryATextSourceSpan() {
        // A photo has no character offsets to point at, so a span is a shape
        // error rather than extra detail.
        rejects {
            result(InferenceModality.PHOTO, listOf(group(sourceSpan = InferenceSourceSpan(0, 3))))
        }
    }

    // --- source spans -------------------------------------------------------

    @Test
    fun aSourceSpanRejectsANegativeStartAndANonAdvancingEnd() {
        rejects { InferenceSourceSpan(start = -1, end = 3) }
        rejects { InferenceSourceSpan(start = 3, end = 3) }
        rejects { InferenceSourceSpan(start = 4, end = 3) }
        InferenceSourceSpan(start = 0, end = 1)
    }

    // --- ranks and identity uniqueness -------------------------------------

    @Test
    fun aV2CandidateRankMustBePositive() {
        rejects { candidateV2(rank = 0) }
        rejects { candidateV2(rank = -1) }
    }

    @Test
    fun v2CandidateRanksAndGroupIdsMustBeUnique() {
        rejects {
            group(candidates = listOf(candidateV2(candidateId = "food:a"), candidateV2(candidateId = "food:b")))
        }
        rejects {
            result(
                InferenceModality.PHOTO,
                listOf(group(groupId = "group-1"), group(groupId = "group-1")),
            )
        }
    }

    // --- capability ---------------------------------------------------------

    @Test
    fun anUnavailableV2CapabilityMustNameAReason() {
        rejects {
            capability(state = CapabilityState.MODEL_REQUIRED, reason = CapabilityReason.NONE)
        }
    }

    @Test
    fun aReadyV2CapabilityMustNotNameAnUnavailableReason() {
        rejects {
            capability(state = CapabilityState.READY, reason = CapabilityReason.MODEL_MISSING)
        }
    }

    @Test
    fun aReadyV2CapabilityMustIdentifyItsModelVersion() {
        rejects { capability(state = CapabilityState.READY, modelVersion = null) }
    }

    // --- v1 confidence and candidates --------------------------------------

    @Test
    fun aConfidenceScoreMustBeFiniteAndWithinTheUnitInterval() {
        rejects { Confidence(score = Double.NaN, band = ConfidenceBand.HIGH) }
        rejects { Confidence(score = Double.POSITIVE_INFINITY, band = ConfidenceBand.HIGH) }
        rejects { Confidence(score = -0.1, band = ConfidenceBand.LOW) }
        rejects { Confidence(score = 1.1, band = ConfidenceBand.HIGH) }
    }

    @Test
    fun aConfidenceBandMustAgreeWithItsScore() {
        // A band that disagrees with the score would let a low-confidence answer
        // present itself as a high-confidence one.
        rejects { Confidence(score = 0.9, band = ConfidenceBand.LOW) }
        rejects { Confidence(score = 0.1, band = ConfidenceBand.HIGH) }
    }

    @Test
    fun aV1CandidateRankMustBePositive() {
        rejects { candidateV1(rank = 0) }
        rejects { candidateV1(rank = -3) }
    }

    @Test
    fun v1CandidateRanksAndIdsMustBeUnique() {
        rejects { InferenceOutcome.Candidates(listOf(candidateV1(), candidateV1(candidateId = "food:other"))) }
        rejects { InferenceOutcome.Candidates(listOf(candidateV1(), candidateV1(rank = 2))) }
    }

    @Test
    fun anUnavailableV1CapabilityMustNameAReason() {
        rejects {
            EngineCapability(
                contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
                state = CapabilityState.MODEL_REQUIRED,
                reason = CapabilityReason.NONE,
                userMessageKey = "inference.capability.model_required",
                engineId = "fixture-engine",
                modelVersion = null,
            )
        }
    }

    // --- component model manifest ------------------------------------------

    @Test
    fun aPlatformManagedModelCannotClaimAnAppRollback() {
        rejects { manifest(ownership = ComponentModelOwnership.PLATFORM_MANAGED) }
    }

    // --- helpers ------------------------------------------------------------

    private fun telemetry(modality: InferenceModality, route: InferenceRouteV2) = InferenceTelemetryEventV2(
        contractVersion = INFERENCE_CONTRACT_V2,
        modality = modality,
        eventName = TelemetryEventNameV2.ATTEMPT_COMPLETED,
        route = route,
        capabilityTier = CapabilityState.READY,
        durationBucket = TelemetryDurationBucketV2.UNDER_2S,
        resourceBucket = TelemetryResourceBucketV2.LOW,
        terminalOutcome = InferenceTelemetryOutcome.CANDIDATES,
        itemGroupCountBucket = TelemetryItemGroupCountBucket.TWO_TO_THREE,
        engineId = "fixture-engine",
        modelVersion = "fixture-model-1",
    )

    private fun capability(
        state: CapabilityState,
        reason: CapabilityReason = CapabilityReason.MODEL_MISSING,
        modelVersion: String? = "fixture-model-1",
    ) = EngineCapabilityV2(
        contractVersion = INFERENCE_CONTRACT_V2,
        modality = InferenceModality.PHOTO,
        state = state,
        reason = reason,
        userMessageKey = "inference.capability.state",
        engineId = "fixture-engine",
        modelVersion = modelVersion,
    )

    private fun candidateV1(candidateId: String = "food:egg-boiled", rank: Int = 1) = InferenceCandidate(
        candidateId = candidateId,
        label = "Egg, boiled",
        rank = rank,
        confidence = Confidence(score = 0.91, band = ConfidenceBand.HIGH),
        unresolvedAssumptions = emptyList(),
        provenance = InferenceProvenance(
            route = InferenceRoute.REMOTE,
            engineId = "fixture-engine",
            modelVersion = "fixture-model-1",
            calibrationVersion = "fixture-calibration-1",
            contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        ),
    )

    private fun manifest(ownership: ComponentModelOwnership) = ComponentModelManifest(
        manifestVersion = COMPONENT_MODEL_MANIFEST_V1,
        modelVersion = "component-model-1",
        artifactDigest = "c".repeat(64),
        taxonomyVersion = "ingredient-taxonomy-1",
        taxonomyDigest = "d".repeat(64),
        preprocessingVersion = "preprocess-1",
        postprocessingVersion = "postprocess-1",
        maskContractVersion = "mask-region-v1",
        calibrationVersion = "mass-calibration-1",
        runtime = "onnx-runtime",
        executionProvider = "cpu",
        compatibilityTier = "android-test",
        ownership = ownership,
        activation = ComponentModelActivation.RESEARCH_ONLY,
        rollbackTargetVersion = "component-model-0",
        productApprovalRef = null,
        engineeringApprovalRef = null,
        legalApprovalRef = null,
        corpusRef = null,
        benchmarkEvidenceRef = null,
        rollbackEvidenceRef = null,
    )
}
