package com.supernutrition.domain.inference

internal object InferenceContractFixtures {
    private val provenance = InferenceProvenance(
        route = InferenceRoute.LOCAL,
        engineId = "fixture-engine",
        modelVersion = "fixture-model-1",
        calibrationVersion = "fixture-calibration-1",
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
    )

    val candidateResult = PhotoInferenceResult(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        outcome = InferenceOutcome.Candidates(
            candidates = listOf(
                InferenceCandidate(
                    candidateId = "food:banana",
                    label = "Banana",
                    rank = 1,
                    confidence = Confidence(0.93, ConfidenceBand.HIGH),
                    unresolvedAssumptions = listOf(
                        InferenceAssumption(
                            code = AssumptionCode.PORTION_UNKNOWN,
                            userMessageKey = "inference.assumption.portion_unknown",
                        ),
                    ),
                    provenance = provenance,
                ),
            ),
        ),
    )

    val abstentionResult = PhotoInferenceResult(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        outcome = InferenceOutcome.Abstention(
            reason = AbstentionReason.PORTION_AMBIGUOUS,
            userMessageKey = "inference.abstention.portion_ambiguous",
            provenance = provenance,
        ),
    )

    val failureResult = PhotoInferenceResult(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        outcome = InferenceOutcome.Failure(
            code = InferenceFailureCode.TEMPORARY_UNAVAILABLE,
            retryable = true,
            userMessageKey = "inference.failure.temporary_unavailable",
            provenance = provenance,
        ),
    )

    val telemetryEvent = InferenceTelemetryEvent(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        eventType = InferenceTelemetryEventType.ATTEMPT,
        route = InferenceRoute.LOCAL,
        capabilityState = CapabilityState.READY,
        outcome = InferenceTelemetryOutcome.CANDIDATES,
        durationBucket = TelemetryDurationBucket.UNDER_1S,
        resourceBucket = TelemetryResourceBucket.WITHIN_BUDGET,
    )

    val readyCapability = EngineCapability(
        contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
        state = CapabilityState.READY,
        reason = CapabilityReason.NONE,
        userMessageKey = "inference.capability.ready",
        engineId = "fixture-engine",
        modelVersion = "fixture-model-1",
    )
}
