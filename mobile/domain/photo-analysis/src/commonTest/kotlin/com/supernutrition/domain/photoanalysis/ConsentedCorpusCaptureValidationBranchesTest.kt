package com.supernutrition.domain.photoanalysis

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConsentedCorpusCaptureValidationBranchesTest {
    @Test
    fun `primitive corpus values reject invalid validation branches`() {
        assertFailsWith<IllegalArgumentException> { CorpusOpaqueRef.of("a:") }
        assertFailsWith<IllegalArgumentException> { CorpusSha256Digest.of("sha256:") }
        assertFailsWith<IllegalArgumentException> {
            CorpusConsent(
                consentRef = ref("consent:one"),
                purpose = CorpusConsentPurpose.MODEL_TRAINING_AND_EVALUATION,
                retentionPolicyVersion = "",
                decision = CorpusConsentDecision.DECLINED,
                retentionStatus = CorpusRetentionStatus.ACTIVE,
                withdrawalStatus = CorpusWithdrawalStatus.ACTIVE,
                derivedArtifactsAllowed = false,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CorpusConsent(
                consentRef = ref("consent:one"),
                purpose = CorpusConsentPurpose.MODEL_TRAINING_AND_EVALUATION,
                retentionPolicyVersion = "retention-1",
                decision = CorpusConsentDecision.GRANTED,
                retentionStatus = CorpusRetentionStatus.ACTIVE,
                withdrawalStatus = CorpusWithdrawalStatus.ACTIVE,
                derivedArtifactsAllowed = false,
            )
        }
        val declined = consent(CorpusConsentDecision.DECLINED, derivedArtifactsAllowed = false)
        assertEquals(false, declined.isUploadEligible)

        assertFailsWith<IllegalArgumentException> { view().copy(widthPx = 0) }
        assertFailsWith<IllegalArgumentException> { box(0.5, 0.2, 0.4, 0.8) }
        assertFailsWith<IllegalArgumentException> { box(-0.1, 0.2, 0.4, 0.8) }
        assertFailsWith<IllegalArgumentException> { mask().copy(heightPx = 0) }
        assertFailsWith<IllegalArgumentException> { CorpusGroundTruthMass.Measured(0.0) }
        assertFailsWith<IllegalArgumentException> { reading("component:one", 0.0) }
        assertFailsWith<IllegalArgumentException> {
            CorpusComponentScaleReading(
                componentRef = ref("component:one"),
                grams = 10.0,
                readBeforePlating = false,
            )
        }
    }

    @Test
    fun `tared scale rejects invalid measurements and duplicate readings`() {
        assertFailsWith<IllegalArgumentException> { measurement(scaleResolutionGrams = 0.0) }
        assertFailsWith<IllegalArgumentException> { measurement(tareMassGrams = -1.0) }
        assertFailsWith<IllegalArgumentException> { measurement(finalPlateMassGrams = -1.0) }
        assertFailsWith<IllegalArgumentException> {
            CorpusTaredScaleMeasurement(
                scaleRef = ref("scale:one"),
                scaleResolutionGrams = 1.0,
                calibrationCheck = CorpusCalibrationCheck.PASSED,
                tareMassGrams = 100.0,
                finalPlateMassGrams = 220.0,
                componentReadings = emptyList(),
                reconciliationToleranceGrams = 1.0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            measurement(readings = listOf(reading("component:one", 20.0), reading("component:one", 20.0)))
        }
        assertFailsWith<IllegalArgumentException> { measurement(reconciliationToleranceGrams = -1.0) }
        assertFailsWith<IllegalArgumentException> { measurement(finalPlateMassGrams = 221.0) }
    }

    @Test
    fun `capture rejects empty collections duplicate references and unmeasured components`() {
        val valid = capture()
        assertFailsWith<IllegalArgumentException> { valid.copy(views = emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(views = listOf(view(), view()))
        }
        assertFailsWith<IllegalArgumentException> { valid.copy(components = emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(components = listOf(component(), component()))
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(components = listOf(nonEvaluableComponent()))
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(
                measurements = valid.measurements.copy(
                    componentReadings = listOf(reading("component:other", 20.0)),
                    finalPlateMassGrams = 220.0,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(
                components = listOf(component().copy(unknownReason = CorpusNotEvaluableReason.UNKNOWN_FOOD)),
            )
        }
    }

    @Test
    fun `upload batch accepts same partition groups and rejects empty or duplicate cases`() {
        val first = capture()
        val second = first.copy(caseRef = ref("meal:case-002"))

        assertEquals(2, CorpusUploadBatch(listOf(first, second)).captures.size)
        assertFailsWith<IllegalArgumentException> { CorpusUploadBatch(emptyList()) }
        assertFailsWith<IllegalArgumentException> { CorpusUploadBatch(listOf(first, first)) }
    }

    private fun capture(): ConsentedCorpusCapture = ConsentedCorpusCapture(
        caseRef = ref("meal:case-001"),
        consent = consent(CorpusConsentDecision.GRANTED, derivedArtifactsAllowed = true),
        views = listOf(view()),
        measurements = measurement(),
        components = listOf(component(), nonEvaluableComponent()),
        quality = CorpusQualityReview(
            privacyReviewPassed = true,
            duplicateReviewPassed = true,
            primaryAnnotatorRef = ref("annotator:one"),
            secondaryAnnotatorRef = ref("annotator:two"),
            adjudicated = true,
            maskAgreementIoU = 1.0,
            identityAgreement = true,
        ),
        split = CorpusSplit(
            partition = CorpusPartition.TRAIN,
            participantGroup = ref("participant:one"),
            mealGroup = ref("meal-group:one"),
            recipeGroup = ref("recipe:one"),
            deviceGroup = ref("device-group:one"),
            sessionGroup = ref("session:one"),
            venueGroup = ref("venue:one"),
        ),
    )

    private fun consent(decision: CorpusConsentDecision, derivedArtifactsAllowed: Boolean) = CorpusConsent(
        consentRef = ref("consent:one"),
        purpose = CorpusConsentPurpose.MODEL_TRAINING_AND_EVALUATION,
        retentionPolicyVersion = "retention-1",
        decision = decision,
        retentionStatus = CorpusRetentionStatus.ACTIVE,
        withdrawalStatus = CorpusWithdrawalStatus.ACTIVE,
        derivedArtifactsAllowed = derivedArtifactsAllowed,
    )

    private fun view() = CorpusCaptureView(
        viewRef = ref("view:one"),
        photoDigest = digest("a"),
        deviceRef = ref("device:one"),
        angle = CorpusCaptureAngle.TOP_DOWN,
        lighting = CorpusCaptureLighting.INDOOR,
        widthPx = 100,
        heightPx = 100,
        depthAvailable = false,
        referencePresent = true,
    )

    private fun box(left: Double, top: Double, right: Double, bottom: Double) =
        CorpusNormalizedBox(left, top, right, bottom)

    private fun mask() = CorpusMaskArtifact(
        digest = digest("b"),
        format = CorpusMaskFormat.COCO_RLE,
        widthPx = 100,
        heightPx = 100,
    )

    private fun component() = CorpusComponentAnnotation(
        componentRef = ref("component:one"),
        state = CorpusComponentState.VISIBLE_SEPARABLE,
        label = "apple",
        unknownReason = null,
        box = box(0.1, 0.1, 0.8, 0.8),
        mask = mask(),
        groundTruthMass = CorpusGroundTruthMass.Measured(20.0),
    )

    private fun nonEvaluableComponent() = CorpusComponentAnnotation(
        componentRef = ref("component:mixed"),
        state = CorpusComponentState.INSEPARABLY_MIXED,
        label = null,
        unknownReason = CorpusNotEvaluableReason.MIXED_PREPARATION,
        box = null,
        mask = null,
        groundTruthMass = CorpusGroundTruthMass.NotEvaluable(CorpusNotEvaluableReason.MIXED_PREPARATION),
    )

    private fun measurement(
        scaleResolutionGrams: Double = 1.0,
        tareMassGrams: Double = 100.0,
        finalPlateMassGrams: Double = 120.0,
        readings: List<CorpusComponentScaleReading> = listOf(reading("component:one", 20.0)),
        reconciliationToleranceGrams: Double = 1.0,
    ) = CorpusTaredScaleMeasurement(
        scaleRef = ref("scale:one"),
        scaleResolutionGrams = scaleResolutionGrams,
        calibrationCheck = CorpusCalibrationCheck.PASSED,
        tareMassGrams = tareMassGrams,
        finalPlateMassGrams = finalPlateMassGrams,
        componentReadings = readings,
        reconciliationToleranceGrams = reconciliationToleranceGrams,
    )

    private fun reading(component: String, grams: Double) = CorpusComponentScaleReading(
        componentRef = ref(component),
        grams = grams,
        readBeforePlating = true,
    )

    private fun ref(value: String) = CorpusOpaqueRef.of(value)

    private fun digest(seed: String) = CorpusSha256Digest.of("sha256:" + seed.repeat(64).take(64))
}
