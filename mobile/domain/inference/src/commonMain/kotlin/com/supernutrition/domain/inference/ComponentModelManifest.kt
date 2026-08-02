package com.supernutrition.domain.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val COMPONENT_MODEL_MANIFEST_V1 = "component-model/v1"

/** Ownership of the artifact and its rollback lifecycle. */
@Serializable
enum class ComponentModelOwnership {
    @SerialName("app-managed")
    APP_MANAGED,

    @SerialName("platform-managed")
    PLATFORM_MANAGED,
}

/** Activation is deliberately separate from capability; a staged model is not production approval. */
@Serializable
enum class ComponentModelActivation {
    @SerialName("research-only")
    RESEARCH_ONLY,

    @SerialName("staged")
    STAGED,

    @SerialName("production")
    PRODUCTION,
}

/**
 * Immutable evidence required before a component/mass model can be selected.
 * This is metadata only: model bytes and runtime objects remain adapter-owned.
 */
@Serializable
data class ComponentModelManifest(
    val manifestVersion: String,
    val modelVersion: String,
    val artifactDigest: String,
    val taxonomyVersion: String,
    val taxonomyDigest: String,
    val preprocessingVersion: String,
    val postprocessingVersion: String,
    val maskContractVersion: String,
    val calibrationVersion: String,
    val runtime: String,
    val executionProvider: String,
    val compatibilityTier: String,
    val ownership: ComponentModelOwnership,
    val activation: ComponentModelActivation,
    val rollbackTargetVersion: String? = null,
    val productApprovalRef: String? = null,
    val engineeringApprovalRef: String? = null,
    val legalApprovalRef: String?,
    val corpusRef: String?,
    val benchmarkEvidenceRef: String?,
    val rollbackEvidenceRef: String?,
) {
    init {
        require(manifestVersion == COMPONENT_MODEL_MANIFEST_V1) {
            "Component model manifest must use component-model/v1"
        }
        requireV2Identifier(modelVersion, "Component model version must be a bounded identifier")
        requireSha256Digest(artifactDigest, "Component artifact digest must be a lowercase SHA-256 digest")
        requireV2Identifier(taxonomyVersion, "Component taxonomy version must be a bounded identifier")
        requireSha256Digest(taxonomyDigest, "Component taxonomy digest must be a lowercase SHA-256 digest")
        requireV2Identifier(preprocessingVersion, "Component preprocessing version must be a bounded identifier")
        requireV2Identifier(postprocessingVersion, "Component postprocessing version must be a bounded identifier")
        requireV2Identifier(maskContractVersion, "Component mask contract version must be a bounded identifier")
        requireV2Identifier(calibrationVersion, "Component calibration version must be a bounded identifier")
        requireV2Identifier(runtime, "Component runtime must be a bounded identifier")
        requireV2Identifier(executionProvider, "Component execution provider must be a bounded identifier")
        requireV2Identifier(compatibilityTier, "Component compatibility tier must be a bounded identifier")

        rollbackTargetVersion?.let {
            requireV2Identifier(it, "Component rollback target must be a bounded identifier")
            require(it != modelVersion) { "Component rollback target must differ from the active model" }
        }

        when (ownership) {
            ComponentModelOwnership.APP_MANAGED -> Unit

            ComponentModelOwnership.PLATFORM_MANAGED -> require(rollbackTargetVersion == null) {
                "Platform-managed component models cannot claim app rollback"
            }
        }

        when (activation) {
            ComponentModelActivation.RESEARCH_ONLY,
            ComponentModelActivation.STAGED,
            -> {
                requireNoProductionReferences(
                    productApprovalRef,
                    engineeringApprovalRef,
                    legalApprovalRef,
                    corpusRef,
                    benchmarkEvidenceRef,
                    rollbackEvidenceRef,
                )
            }

            ComponentModelActivation.PRODUCTION -> {
                require(ownership == ComponentModelOwnership.APP_MANAGED) {
                    "Production component models must have an app-managed rollback lifecycle"
                }
                requireV2OpaqueReference(productApprovalRef, "Production component manifest needs product approval")
                requireV2OpaqueReference(
                    engineeringApprovalRef,
                    "Production component manifest needs engineering approval",
                )
                requireV2OpaqueReference(legalApprovalRef, "Production component manifest needs legal approval")
                requireV2OpaqueReference(corpusRef, "Production component manifest needs a consented corpus")
                requireV2OpaqueReference(
                    benchmarkEvidenceRef,
                    "Production component manifest needs benchmark evidence",
                )
                requireV2OpaqueReference(
                    rollbackEvidenceRef,
                    "Production component manifest needs rollback evidence",
                )
                requireNotNull(rollbackTargetVersion) {
                    "Production component manifest needs a rollback target"
                }
            }
        }
    }

    fun toProvenance(engineId: String, route: InferenceRoute = InferenceRoute.LOCAL): InferenceV2Provenance =
        InferenceV2Provenance(
            route = route,
            engineId = engineId,
            modelVersion = modelVersion,
            taxonomyVersion = taxonomyVersion,
            calibrationVersion = calibrationVersion,
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        )
}

private fun requireV2OpaqueReference(value: String?, message: String) {
    require(value != null && value.isNotBlank() && value.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) {
        message
    }
}

private fun requireNoProductionReferences(vararg values: String?) {
    require(values.all { it == null }) {
        "Non-production component manifests cannot carry production evidence"
    }
}
