package com.supernutrition.domain.photoanalysis

import kotlin.math.abs

/** Versioned, redacted handoff contract for the post-release corpus seam. */
const val CONSENTED_CORPUS_CAPTURE_CONTRACT_V1 = "consented-weighed-component-corpus/v1"

private val OPAQUE_REFERENCE_PATTERN = Regex("^[a-z][a-z0-9-]*:[a-z0-9][a-z0-9._-]{1,127}$")
private val DIGEST_PATTERN = Regex("^sha256:[0-9a-f]{64}$")
private val TOKEN_PATTERN = Regex("^[A-Za-z0-9._:-]{1,128}$")
private val CANONICAL_LABEL_PATTERN = Regex("^[a-z0-9]+(?:[_-][a-z0-9]+)*$")

/** An identifier whose lookup table remains outside the product and repository. */
class CorpusOpaqueRef private constructor(val value: String) {
    override fun equals(other: Any?): Boolean = other is CorpusOpaqueRef && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "CorpusOpaqueRef(redacted)"

    companion object {
        fun of(value: String): CorpusOpaqueRef = CorpusOpaqueRef(
            value.also {
                require(OPAQUE_REFERENCE_PATTERN.matches(it)) {
                    "Corpus references must be bounded opaque identifiers"
                }
            },
        )
    }
}

/** A scrubbed artifact digest; the raw photo or mask is never part of this model. */
class CorpusSha256Digest private constructor(val value: String) {
    override fun equals(other: Any?): Boolean = other is CorpusSha256Digest && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "CorpusSha256Digest(redacted)"

    companion object {
        fun of(value: String): CorpusSha256Digest = CorpusSha256Digest(
            value.also {
                require(DIGEST_PATTERN.matches(it)) {
                    "Corpus artifact digests must use lowercase sha256:<64 hex>"
                }
            },
        )
    }
}

enum class CorpusConsentPurpose {
    MODEL_TRAINING_AND_EVALUATION,
}

enum class CorpusConsentDecision {
    GRANTED,
    DECLINED,
}

enum class CorpusRetentionStatus {
    ACTIVE,
    EXPIRED,
}

enum class CorpusWithdrawalStatus {
    ACTIVE,
    WITHDRAWN,
}

/** Consent is a required, explicit input; there is no implicit upload default. */
data class CorpusConsent(
    val consentRef: CorpusOpaqueRef,
    val purpose: CorpusConsentPurpose,
    val retentionPolicyVersion: String,
    val decision: CorpusConsentDecision,
    val retentionStatus: CorpusRetentionStatus,
    val withdrawalStatus: CorpusWithdrawalStatus,
    val derivedArtifactsAllowed: Boolean,
) {
    init {
        require(TOKEN_PATTERN.matches(retentionPolicyVersion)) {
            "Retention policy version must be a bounded identifier"
        }
        if (decision == CorpusConsentDecision.GRANTED) {
            require(derivedArtifactsAllowed) {
                "Granted corpus consent must allow derived artifacts"
            }
        }
    }

    val isUploadEligible: Boolean
        get() = decision == CorpusConsentDecision.GRANTED &&
            retentionStatus == CorpusRetentionStatus.ACTIVE &&
            withdrawalStatus == CorpusWithdrawalStatus.ACTIVE &&
            derivedArtifactsAllowed

    fun withdraw(): CorpusConsent = copy(withdrawalStatus = CorpusWithdrawalStatus.WITHDRAWN)

    fun expireRetention(): CorpusConsent = copy(retentionStatus = CorpusRetentionStatus.EXPIRED)
}

enum class CorpusCaptureAngle {
    TOP_DOWN,
    OBLIQUE,
    MULTI_VIEW,
}

enum class CorpusCaptureLighting {
    DAYLIGHT,
    INDOOR,
    MIXED,
    LOW_LIGHT,
}

data class CorpusCaptureView(
    val viewRef: CorpusOpaqueRef,
    val photoDigest: CorpusSha256Digest,
    val deviceRef: CorpusOpaqueRef,
    val angle: CorpusCaptureAngle,
    val lighting: CorpusCaptureLighting,
    val widthPx: Int,
    val heightPx: Int,
    val depthAvailable: Boolean,
    val referencePresent: Boolean,
) {
    init {
        require(widthPx > 0 && heightPx > 0) { "Capture dimensions must be positive" }
    }
}

data class CorpusNormalizedBox(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    init {
        require(left in 0.0..1.0 && top in 0.0..1.0 && right in 0.0..1.0 && bottom in 0.0..1.0) {
            "Component box must use normalized coordinates"
        }
        require(left < right && top < bottom) { "Component box must have positive area" }
    }
}

enum class CorpusMaskFormat {
    COCO_RLE,
    COCO_POLYGON,
}

data class CorpusMaskArtifact(
    val digest: CorpusSha256Digest,
    val format: CorpusMaskFormat,
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(widthPx > 0 && heightPx > 0) { "Mask dimensions must be positive" }
    }
}

enum class CorpusComponentState {
    VISIBLE_SEPARABLE,
    HIDDEN,
    OCCLUDED,
    UNKNOWN,
    INSEPARABLY_MIXED,
}

enum class CorpusNotEvaluableReason {
    HIDDEN,
    OCCLUDED,
    UNKNOWN_FOOD,
    MIXED_PREPARATION,
    NOT_VISIBLY_SEPARABLE,
}

sealed interface CorpusGroundTruthMass {
    data class Measured(val grams: Double) : CorpusGroundTruthMass {
        init {
            require(grams.isFinite() && grams > 0.0) { "Measured component mass must be positive and finite" }
        }
    }

    data class NotEvaluable(val reason: CorpusNotEvaluableReason) : CorpusGroundTruthMass
}

data class CorpusComponentAnnotation(
    val componentRef: CorpusOpaqueRef,
    val state: CorpusComponentState,
    val label: String?,
    val unknownReason: CorpusNotEvaluableReason?,
    val box: CorpusNormalizedBox?,
    val mask: CorpusMaskArtifact?,
    val groundTruthMass: CorpusGroundTruthMass,
) {
    init {
        if (state == CorpusComponentState.VISIBLE_SEPARABLE) {
            require(label != null && CANONICAL_LABEL_PATTERN.matches(label)) {
                "Visible component must have a canonical taxonomy label"
            }
            require(unknownReason == null) { "Visible component cannot have a not-evaluable reason" }
            require(box != null && mask != null) { "Visible component must have a box and mask digest" }
            require(groundTruthMass is CorpusGroundTruthMass.Measured) {
                "Visible component must have measured ground truth mass"
            }
        } else {
            require(label == null && box == null && mask == null) {
                "Non-evaluable component cannot carry visible identity or region evidence"
            }
            val reason = requireNotNull(unknownReason) { "Non-evaluable component needs a typed reason" }
            require(groundTruthMass == CorpusGroundTruthMass.NotEvaluable(reason)) {
                "Non-evaluable mass must repeat its typed reason"
            }
        }
    }

    val isEvaluable: Boolean
        get() = state == CorpusComponentState.VISIBLE_SEPARABLE
}

data class CorpusComponentScaleReading(
    val componentRef: CorpusOpaqueRef,
    val grams: Double,
    val readBeforePlating: Boolean,
) {
    init {
        require(grams.isFinite() && grams > 0.0) { "Component scale reading must be positive and finite" }
        require(readBeforePlating) { "Component scale readings must be taken before plating" }
    }
}

enum class CorpusCalibrationCheck {
    PASSED,
    FAILED,
}

data class CorpusTaredScaleMeasurement(
    val scaleRef: CorpusOpaqueRef,
    val scaleResolutionGrams: Double,
    val calibrationCheck: CorpusCalibrationCheck,
    val tareMassGrams: Double,
    val finalPlateMassGrams: Double,
    val componentReadings: List<CorpusComponentScaleReading>,
    val reconciliationToleranceGrams: Double,
) {
    init {
        require(scaleResolutionGrams.isFinite() && scaleResolutionGrams > 0.0) {
            "Scale resolution must be positive and finite"
        }
        require(calibrationCheck == CorpusCalibrationCheck.PASSED) {
            "Corpus scale calibration must pass before capture upload"
        }
        require(tareMassGrams.isFinite() && tareMassGrams >= 0.0) {
            "Tare mass must be finite and non-negative"
        }
        require(finalPlateMassGrams.isFinite() && finalPlateMassGrams >= 0.0) {
            "Final plate mass must be finite and non-negative"
        }
        require(componentReadings.isNotEmpty()) { "At least one measured component reading is required" }
        require(componentReadings.map { it.componentRef }.distinct().size == componentReadings.size) {
            "Component scale readings must be one-to-one"
        }
        require(reconciliationToleranceGrams.isFinite() && reconciliationToleranceGrams >= 0.0) {
            "Reconciliation tolerance must be finite and non-negative"
        }
        require(abs(reconciliationDeltaGrams) <= reconciliationToleranceGrams) {
            "Tared component readings do not reconcile with final plate mass"
        }
    }

    /** final plated mass minus tare and the independently measured components. */
    val reconciliationDeltaGrams: Double
        get() = finalPlateMassGrams - tareMassGrams - componentReadings.sumOf { it.grams }
}

data class CorpusQualityReview(
    val privacyReviewPassed: Boolean,
    val duplicateReviewPassed: Boolean,
    val primaryAnnotatorRef: CorpusOpaqueRef,
    val secondaryAnnotatorRef: CorpusOpaqueRef,
    val adjudicated: Boolean,
    val maskAgreementIoU: Double,
    val identityAgreement: Boolean,
) {
    init {
        require(privacyReviewPassed && duplicateReviewPassed) { "Corpus privacy and duplicate review must pass" }
        require(primaryAnnotatorRef != secondaryAnnotatorRef) { "Independent annotators must be distinct" }
        require(adjudicated) { "Corpus annotation disagreements must be adjudicated" }
        require(maskAgreementIoU.isFinite() && maskAgreementIoU in 0.0..1.0) {
            "Mask agreement IoU must be between zero and one"
        }
        require(identityAgreement) { "Independent identity review must agree" }
    }
}

enum class CorpusPartition {
    TRAIN,
    CALIBRATION,
    HELD_OUT,
}

data class CorpusSplit(
    val partition: CorpusPartition,
    val participantGroup: CorpusOpaqueRef,
    val mealGroup: CorpusOpaqueRef,
    val recipeGroup: CorpusOpaqueRef,
    val deviceGroup: CorpusOpaqueRef,
    val sessionGroup: CorpusOpaqueRef,
    val venueGroup: CorpusOpaqueRef,
)

/** One complete redacted case; no raw photo, path, identity, or network object can enter it. */
data class ConsentedCorpusCapture(
    val caseRef: CorpusOpaqueRef,
    val consent: CorpusConsent,
    val views: List<CorpusCaptureView>,
    val measurements: CorpusTaredScaleMeasurement,
    val components: List<CorpusComponentAnnotation>,
    val quality: CorpusQualityReview,
    val split: CorpusSplit,
) {
    init {
        require(consent.decision == CorpusConsentDecision.GRANTED) {
            "Corpus capture requires an explicit granted consent decision"
        }
        require(views.isNotEmpty()) { "Corpus capture needs at least one scrubbed photo digest" }
        require(views.map { it.viewRef }.distinct().size == views.size) { "Capture view references must be unique" }
        require(components.isNotEmpty()) { "Corpus capture needs at least one component" }
        require(components.map { it.componentRef }.distinct().size == components.size) {
            "Component references must be unique"
        }

        val measured = components.filter { it.isEvaluable }
        val readingsByRef = measurements.componentReadings.associateBy { it.componentRef }
        require(measured.isNotEmpty()) { "Corpus capture needs at least one measured visible component" }
        require(measured.map { it.componentRef }.toSet() == readingsByRef.keys) {
            "Every measured visible component needs exactly one independent scale reading"
        }
        measured.forEach { component ->
            val mass = component.groundTruthMass as CorpusGroundTruthMass.Measured
            require(mass.grams == readingsByRef.getValue(component.componentRef).grams) {
                "Component annotation mass must match its independent scale reading"
            }
        }
    }

    val isUploadEligible: Boolean
        get() = consent.isUploadEligible

    fun withdraw(): ConsentedCorpusCapture = copy(consent = consent.withdraw())

    fun expireRetention(): ConsentedCorpusCapture = copy(consent = consent.expireRetention())

    suspend fun upload(port: ConsentedCorpusUploadPort): CorpusUploadReceipt =
        port.upload(CorpusUploadBatch(listOf(this)))
}

/** Batch boundary used to enforce cross-case split isolation before an adapter sees data. */
data class CorpusUploadBatch(val captures: List<ConsentedCorpusCapture>) {
    init {
        require(captures.isNotEmpty()) { "Upload batch must not be empty" }
        require(captures.map { it.caseRef }.distinct().size == captures.size) {
            "Upload batch case references must be unique"
        }
        captures.forEach {
            require(it.isUploadEligible) { "Withdrawn, expired, or unconsented captures cannot upload" }
        }
        validatePartitionIsolation(captures)
    }

    private fun validatePartitionIsolation(captures: List<ConsentedCorpusCapture>) {
        val seen = mutableMapOf<Pair<String, CorpusOpaqueRef>, CorpusPartition>()
        captures.forEach { capture ->
            val split = capture.split
            listOf(
                "participant" to split.participantGroup,
                "meal" to split.mealGroup,
                "recipe" to split.recipeGroup,
                "device" to split.deviceGroup,
                "session" to split.sessionGroup,
                "venue" to split.venueGroup,
            ).forEach { (groupType, groupRef) ->
                val key = groupType to groupRef
                // Not putIfAbsent: that is a JVM-only MutableMap extension, and
                // this file is commonMain — so :domain:photo-analysis could
                // never compile for Kotlin/Native. Nothing noticed because the
                // sharedKit host slice failed to configure before reaching any
                // Kotlin at all.
                val previous = seen[key]
                if (previous == null) seen[key] = split.partition
                require(previous == null || previous == split.partition) {
                    "$groupType group must not cross corpus partitions"
                }
            }
        }
    }
}

data class CorpusUploadReceipt(val uploadRef: CorpusOpaqueRef, val payloadDigest: CorpusSha256Digest)

/** Domain port only. A platform/backend adapter must be supplied after explicit product approval. */
interface ConsentedCorpusUploadPort {
    suspend fun upload(batch: CorpusUploadBatch): CorpusUploadReceipt
}
