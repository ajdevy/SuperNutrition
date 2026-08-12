package com.supernutrition.domain.inference

internal object InferenceV2ContractFixtures {
    private val provenance = InferenceV2Provenance(
        route = InferenceRoute.LOCAL,
        engineId = "fixture-component-engine",
        modelVersion = "fixture-component-model-1",
        taxonomyVersion = "fixture-ingredient-taxonomy-1",
        calibrationVersion = "fixture-mass-calibration-1",
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
    )

    val estimatedPhotoResult = InferenceV2Result(
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        inputModality = InferenceInputModality.PHOTO,
        outcome = InferenceV2Outcome.Components(
            components = listOf(
                VisibleComponent(
                    componentId = "component:salmon-1",
                    candidate = LocalizedCandidateIdentity(
                        candidateId = "ingredient:salmon",
                        label = "Salmon",
                        locale = "en",
                    ),
                    confidence = CalibratedConfidence(0.91, ConfidenceBand.HIGH),
                    visibility = ComponentVisibility.VISIBLE,
                    ambiguity = ComponentAmbiguity.NONE,
                    region = NormalizedVisibleRegion(0.1, 0.2, 0.3, 0.4),
                    mass = MassEvidence.Estimated(
                        range = GramRange(lowerGrams = 120.0, upperGrams = 155.0),
                        calibrationAssumptions = listOf(
                            MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION,
                        ),
                    ),
                    provenance = provenance,
                ),
            ),
        ),
    )

    val notEvaluablePhotoResult = estimatedPhotoResult.copy(
        outcome = InferenceV2Outcome.Components(
            components = listOf(
                (estimatedPhotoResult.outcome as InferenceV2Outcome.Components).components.single().copy(
                    componentId = "component:vegetables-1",
                    candidate = LocalizedCandidateIdentity(
                        candidateId = "ingredient:mixed-vegetables",
                        label = "Mixed vegetables",
                        locale = "en",
                    ),
                    mass = MassEvidence.NotEvaluable(
                        reason = MassNotEvaluableReason.MIXED_OR_OCCLUDED,
                        userMessageKey = "inference.mass.mixed_or_occluded",
                    ),
                ),
            ),
        ),
    )

    val textNotEvaluableResult = notEvaluablePhotoResult.copy(
        inputModality = InferenceInputModality.TEXT,
        outcome = InferenceV2Outcome.Components(
            components = listOf(
                (notEvaluablePhotoResult.outcome as InferenceV2Outcome.Components).components.single().copy(
                    componentId = "component:beans-1",
                    candidate = LocalizedCandidateIdentity(
                        candidateId = "ingredient:black-beans",
                        label = "Black beans",
                        locale = "en",
                    ),
                    region = null,
                    mass = MassEvidence.NotEvaluable(
                        reason = MassNotEvaluableReason.TEXT_HAS_NO_VISIBLE_PORTION,
                        userMessageKey = "inference.mass.text_has_no_visible_portion",
                    ),
                ),
            ),
        ),
    )

    val abstentionResult = InferenceV2Result(
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        inputModality = InferenceInputModality.PHOTO,
        outcome = InferenceV2Outcome.Abstention(
            reason = V2AbstentionReason.NO_FOOD_DETECTED,
            userMessageKey = "inference.abstention.no_food_detected",
            provenance = provenance,
        ),
    )

    val failureResult = InferenceV2Result(
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        inputModality = InferenceInputModality.PHOTO,
        outcome = InferenceV2Outcome.Failure(
            code = V2InferenceFailureCode.TEMPORARY_UNAVAILABLE,
            retryable = true,
            userMessageKey = "inference.failure.temporary_unavailable",
            provenance = provenance,
        ),
    )

    val readyCapability = InferenceV2Capability(
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        state = CapabilityState.READY,
        reason = CapabilityReason.NONE,
        userMessageKey = "inference.capability.ready",
        engineId = "fixture-component-engine",
        modelVersion = "fixture-component-model-1",
        taxonomyVersion = "fixture-ingredient-taxonomy-1",
        calibrationVersion = "fixture-mass-calibration-1",
    )

    val telemetryEvent = InferenceV2TelemetryEvent(
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        eventType = InferenceTelemetryEventType.ATTEMPT,
        route = InferenceRoute.LOCAL,
        capabilityState = CapabilityState.READY,
        outcome = InferenceV2TelemetryOutcome.COMPONENTS,
        durationBucket = TelemetryDurationBucket.FROM_1_TO_5S,
        resourceBucket = TelemetryResourceBucket.WITHIN_BUDGET,
        componentCountBucket = ComponentCountBucket.ONE,
    )
}
