package com.supernutrition.domain.photoanalysis

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConsentedCorpusCaptureTest {
    @Test
    fun `capture keeps only redacted refs digests and reconciled weighed components`() {
        val capture = capture()

        assertEquals("consented-weighed-component-corpus/v1", CONSENTED_CORPUS_CAPTURE_CONTRACT_V1)
        assertEquals(0.0, capture.measurements.reconciliationDeltaGrams)
        assertEquals(2, capture.components.size)
        assertTrue(capture.components.first().isEvaluable)
        assertFalse(capture.components.last().isEvaluable)
        assertEquals(
            CorpusNotEvaluableReason.MIXED_PREPARATION,
            (capture.components.last().groundTruthMass as CorpusGroundTruthMass.NotEvaluable).reason,
        )
        assertEquals("CorpusOpaqueRef(redacted)", capture.caseRef.toString())
        assertEquals("CorpusSha256Digest(redacted)", capture.views.single().photoDigest.toString())
    }

    @Test
    fun `tared measurement rejects failed calibration and unreconciled readings`() {
        assertFailsWith<IllegalArgumentException> {
            CorpusTaredScaleMeasurement(
                scaleRef = ref("scale:one"),
                scaleResolutionGrams = 1.0,
                calibrationCheck = CorpusCalibrationCheck.FAILED,
                tareMassGrams = 100.0,
                finalPlateMassGrams = 300.0,
                componentReadings = listOf(reading("component:beef", 120.0)),
                reconciliationToleranceGrams = 5.0,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CorpusTaredScaleMeasurement(
                scaleRef = ref("scale:one"),
                scaleResolutionGrams = 1.0,
                calibrationCheck = CorpusCalibrationCheck.PASSED,
                tareMassGrams = 100.0,
                finalPlateMassGrams = 300.0,
                componentReadings = listOf(reading("component:beef", 120.0)),
                reconciliationToleranceGrams = 5.0,
            )
        }
    }

    @Test
    fun `capture requires measured components to match independent scale readings`() {
        val valid = capture()
        assertFailsWith<IllegalArgumentException> {
            valid.copy(
                measurements = valid.measurements.copy(
                    componentReadings = listOf(reading("component:beef", 121.0)),
                    finalPlateMassGrams = 221.0,
                ),
            )
        }
    }

    @Test
    fun `visible and non evaluable component states are fail closed`() {
        assertFailsWith<IllegalArgumentException> {
            CorpusComponentAnnotation(
                componentRef = ref("component:beef"),
                state = CorpusComponentState.VISIBLE_SEPARABLE,
                label = null,
                unknownReason = null,
                box = null,
                mask = null,
                groundTruthMass = CorpusGroundTruthMass.NotEvaluable(CorpusNotEvaluableReason.UNKNOWN_FOOD),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            CorpusComponentAnnotation(
                componentRef = ref("component:mixed"),
                state = CorpusComponentState.INSEPARABLY_MIXED,
                label = null,
                unknownReason = CorpusNotEvaluableReason.MIXED_PREPARATION,
                box = null,
                mask = null,
                groundTruthMass = CorpusGroundTruthMass.Measured(20.0),
            )
        }
    }

    @Test
    fun `withdrawal and retention expiry prevent upload`() {
        val port = RecordingUploadPort()
        val withdrawn = capture().withdraw()
        val expired = capture().expireRetention()

        assertEquals(CorpusWithdrawalStatus.WITHDRAWN, withdrawn.consent.withdrawalStatus)
        assertEquals(CorpusRetentionStatus.EXPIRED, expired.consent.retentionStatus)
        assertFailsWith<IllegalArgumentException> { runSuspend { withdrawn.upload(port) } }
        assertFailsWith<IllegalArgumentException> { runSuspend { expired.upload(port) } }
        assertEquals(0, port.uploadCount)
    }

    @Test
    fun `upload port receives one immutable eligible redacted batch`() {
        val port = RecordingUploadPort()
        val receipt = runSuspend { capture().upload(port) }

        assertEquals(ref("upload:one"), receipt.uploadRef)
        assertNotNull(port.batch)
        assertEquals(1, port.batch!!.captures.size)
        assertEquals(ref("meal:case-001"), port.batch!!.captures.single().caseRef)
        assertEquals(1, port.uploadCount)
    }

    @Test
    fun `upload batch rejects cross partition group leakage`() {
        val first = capture()
        val second = capture().copy(
            caseRef = ref("meal:case-002"),
            split = first.split.copy(partition = CorpusPartition.CALIBRATION),
        )

        assertFailsWith<IllegalArgumentException> {
            CorpusUploadBatch(listOf(first, second))
        }
    }

    @Test
    fun `opaque identifiers and digests reject paths and unqualified hashes`() {
        assertFailsWith<IllegalArgumentException> { CorpusOpaqueRef.of("/private/photo.jpg") }
        assertFailsWith<IllegalArgumentException> { CorpusOpaqueRef.of("person@example.com") }
        assertFailsWith<IllegalArgumentException> { CorpusSha256Digest.of("a".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { CorpusSha256Digest.of("sha256:" + "A".repeat(64)) }
    }

    private fun capture(): ConsentedCorpusCapture = ConsentedCorpusCapture(
        caseRef = ref("meal:case-001"),
        consent = consent(),
        views = listOf(view()),
        measurements = measurement(),
        components = components(),
        quality = quality(),
        split = split(),
    )

    private fun consent() = CorpusConsent(
        consentRef = ref("consent:case-001"),
        purpose = CorpusConsentPurpose.MODEL_TRAINING_AND_EVALUATION,
        retentionPolicyVersion = "retention-1",
        decision = CorpusConsentDecision.GRANTED,
        retentionStatus = CorpusRetentionStatus.ACTIVE,
        withdrawalStatus = CorpusWithdrawalStatus.ACTIVE,
        derivedArtifactsAllowed = true,
    )

    private fun view() = CorpusCaptureView(
        viewRef = ref("view:top-001"),
        photoDigest = digest("a"),
        deviceRef = ref("device:phone-001"),
        angle = CorpusCaptureAngle.TOP_DOWN,
        lighting = CorpusCaptureLighting.INDOOR,
        widthPx = 1000,
        heightPx = 1000,
        depthAvailable = false,
        referencePresent = true,
    )

    private fun measurement() = CorpusTaredScaleMeasurement(
        scaleRef = ref("scale:one"),
        scaleResolutionGrams = 1.0,
        calibrationCheck = CorpusCalibrationCheck.PASSED,
        tareMassGrams = 100.0,
        finalPlateMassGrams = 220.0,
        componentReadings = listOf(reading("component:beef", 120.0)),
        reconciliationToleranceGrams = 5.0,
    )

    private fun components() = listOf(
        CorpusComponentAnnotation(
            componentRef = ref("component:beef"),
            state = CorpusComponentState.VISIBLE_SEPARABLE,
            label = "beef_steak",
            unknownReason = null,
            box = CorpusNormalizedBox(0.1, 0.1, 0.6, 0.8),
            mask = CorpusMaskArtifact(
                digest = digest("b"),
                format = CorpusMaskFormat.COCO_RLE,
                widthPx = 1000,
                heightPx = 1000,
            ),
            groundTruthMass = CorpusGroundTruthMass.Measured(120.0),
        ),
        CorpusComponentAnnotation(
            componentRef = ref("component:mixed"),
            state = CorpusComponentState.INSEPARABLY_MIXED,
            label = null,
            unknownReason = CorpusNotEvaluableReason.MIXED_PREPARATION,
            box = null,
            mask = null,
            groundTruthMass = CorpusGroundTruthMass.NotEvaluable(
                CorpusNotEvaluableReason.MIXED_PREPARATION,
            ),
        ),
    )

    private fun quality() = CorpusQualityReview(
        privacyReviewPassed = true,
        duplicateReviewPassed = true,
        primaryAnnotatorRef = ref("annotator:primary-001"),
        secondaryAnnotatorRef = ref("annotator:secondary-001"),
        adjudicated = true,
        maskAgreementIoU = 0.9,
        identityAgreement = true,
    )

    private fun split() = CorpusSplit(
        partition = CorpusPartition.TRAIN,
        participantGroup = ref("participant:001"),
        mealGroup = ref("meal-group:001"),
        recipeGroup = ref("recipe:001"),
        deviceGroup = ref("device-group:001"),
        sessionGroup = ref("session:001"),
        venueGroup = ref("venue:001"),
    )

    private fun reading(component: String, grams: Double) = CorpusComponentScaleReading(
        componentRef = ref(component),
        grams = grams,
        readBeforePlating = true,
    )

    private fun ref(value: String) = CorpusOpaqueRef.of(value)

    private fun digest(seed: String) = CorpusSha256Digest.of("sha256:" + (seed.repeat(64)).take(64))

    private class RecordingUploadPort : ConsentedCorpusUploadPort {
        var batch: CorpusUploadBatch? = null
        var uploadCount: Int = 0

        override suspend fun upload(batch: CorpusUploadBatch): CorpusUploadReceipt {
            this.batch = batch
            uploadCount += 1
            return CorpusUploadReceipt(
                CorpusOpaqueRef.of("upload:one"),
                CorpusSha256Digest.of("sha256:" + "a".repeat(64)),
            )
        }
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                outcome = result
            }
        })
        return requireNotNull(outcome).getOrThrow()
    }
}
