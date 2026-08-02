package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ComponentModelManifestTest {
    @Test
    fun `research manifest exposes complete v2 provenance without approval`() {
        val manifest = validManifest()

        assertEquals(COMPONENT_MODEL_MANIFEST_V1, manifest.manifestVersion)
        assertEquals("component-model-research-1", manifest.modelVersion)
        assertEquals(
            InferenceV2Provenance(
                route = InferenceRoute.LOCAL,
                engineId = "local-component-test",
                modelVersion = "component-model-research-1",
                taxonomyVersion = "ingredient-taxonomy-1",
                calibrationVersion = "mass-calibration-1",
                contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
            ),
            manifest.toProvenance("local-component-test"),
        )
    }

    @Test
    fun `production activation requires approvals evidence rollback and app ownership`() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(activation = ComponentModelActivation.PRODUCTION)
        }
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                activation = ComponentModelActivation.PRODUCTION,
                ownership = ComponentModelOwnership.PLATFORM_MANAGED,
                rollbackTargetVersion = null,
                refs = ManifestRefs(
                    productApprovalRef = "approval:product-1",
                    engineeringApprovalRef = "approval:engineering-1",
                    legalApprovalRef = "approval:legal-1",
                    corpusRef = "corpus:weighed-v1",
                    benchmarkEvidenceRef = "evidence:g3-v1",
                    rollbackEvidenceRef = "evidence:rollback-v1",
                ),
            )
        }

        val production = validProductionManifest()
        assertEquals(ComponentModelActivation.PRODUCTION, production.activation)
        assertEquals("component-model-research-0", production.rollbackTargetVersion)
    }

    @Test
    fun `non-production activation cannot smuggle approval references`() {
        assertFailsWith<IllegalArgumentException> {
            validManifest(refs = ManifestRefs(productApprovalRef = "approval:product-1"))
        }
        assertFailsWith<IllegalArgumentException> {
            validManifest(refs = ManifestRefs(engineeringApprovalRef = "approval:engineering-1"))
        }
        assertFailsWith<IllegalArgumentException> {
            validManifest(refs = ManifestRefs(legalApprovalRef = "approval:legal-1"))
        }
        assertFailsWith<IllegalArgumentException> {
            validManifest(refs = ManifestRefs(corpusRef = "corpus:weighed-v1"))
        }
        assertFailsWith<IllegalArgumentException> {
            validManifest(refs = ManifestRefs(benchmarkEvidenceRef = "evidence:g3-v1"))
        }
        assertFailsWith<IllegalArgumentException> {
            validManifest(refs = ManifestRefs(rollbackEvidenceRef = "evidence:rollback-v1"))
        }
    }

    @Test
    fun `manifest rejects digest and rollback drift`() {
        assertFailsWith<IllegalArgumentException> { validManifest().copy(artifactDigest = "not-a-digest") }
        assertFailsWith<IllegalArgumentException> {
            validManifest(rollbackTargetVersion = "component-model-research-1")
        }
        assertFailsWith<IllegalArgumentException> {
            validManifest(
                ownership = ComponentModelOwnership.PLATFORM_MANAGED,
                rollbackTargetVersion = "component-model-research-0",
            )
        }
    }

    @Test
    fun `production manifest rejects missing or malformed evidence references`() {
        assertFailsWith<IllegalArgumentException> {
            validProductionManifest().copy(productApprovalRef = "bad ref")
        }
        assertFailsWith<IllegalArgumentException> {
            validProductionManifest().copy(engineeringApprovalRef = "")
        }
        assertFailsWith<IllegalArgumentException> {
            validProductionManifest().copy(legalApprovalRef = null)
        }
        assertFailsWith<IllegalArgumentException> {
            validProductionManifest().copy(corpusRef = null)
        }
        assertFailsWith<IllegalArgumentException> {
            validProductionManifest().copy(benchmarkEvidenceRef = null)
        }
        assertFailsWith<IllegalArgumentException> {
            validProductionManifest().copy(rollbackEvidenceRef = null)
        }
        assertFailsWith<IllegalArgumentException> {
            validProductionManifest().copy(rollbackTargetVersion = null)
        }
    }

    @Test
    fun `manifest rejects malformed immutable fields individually`() {
        assertFailsWith<IllegalArgumentException> {
            validManifest().copy(manifestVersion = "component-model/v0")
        }
        assertFailsWith<IllegalArgumentException> { validManifest().copy(modelVersion = "") }
        assertFailsWith<IllegalArgumentException> { validManifest().copy(taxonomyVersion = "") }
        assertFailsWith<IllegalArgumentException> {
            validManifest().copy(taxonomyDigest = "not-a-digest")
        }
        assertFailsWith<IllegalArgumentException> { validManifest().copy(preprocessingVersion = "") }
        assertFailsWith<IllegalArgumentException> { validManifest().copy(postprocessingVersion = "") }
        assertFailsWith<IllegalArgumentException> { validManifest().copy(maskContractVersion = "") }
        assertFailsWith<IllegalArgumentException> { validManifest().copy(calibrationVersion = "") }
        assertFailsWith<IllegalArgumentException> { validManifest().copy(runtime = "") }
        assertFailsWith<IllegalArgumentException> { validManifest().copy(executionProvider = "") }
        assertFailsWith<IllegalArgumentException> { validManifest().copy(compatibilityTier = "") }
        assertFailsWith<IllegalArgumentException> { validManifest().copy(rollbackTargetVersion = "") }
        val platformManaged = validManifest(
            ownership = ComponentModelOwnership.PLATFORM_MANAGED,
            rollbackTargetVersion = null,
        )
        assertEquals(ComponentModelOwnership.PLATFORM_MANAGED, platformManaged.ownership)
    }

    private fun validProductionManifest(): ComponentModelManifest = validManifest(
        activation = ComponentModelActivation.PRODUCTION,
        refs = ManifestRefs(
            productApprovalRef = "approval:product-1",
            engineeringApprovalRef = "approval:engineering-1",
            legalApprovalRef = "approval:legal-1",
            corpusRef = "corpus:weighed-v1",
            benchmarkEvidenceRef = "evidence:g3-v1",
            rollbackEvidenceRef = "evidence:rollback-v1",
        ),
    )

    private data class ManifestRefs(
        val productApprovalRef: String? = null,
        val engineeringApprovalRef: String? = null,
        val legalApprovalRef: String? = null,
        val corpusRef: String? = null,
        val benchmarkEvidenceRef: String? = null,
        val rollbackEvidenceRef: String? = null,
    )

    private fun validManifest(
        activation: ComponentModelActivation = ComponentModelActivation.RESEARCH_ONLY,
        ownership: ComponentModelOwnership = ComponentModelOwnership.APP_MANAGED,
        rollbackTargetVersion: String? = "component-model-research-0",
        refs: ManifestRefs = ManifestRefs(),
    ): ComponentModelManifest = ComponentModelManifest(
        manifestVersion = COMPONENT_MODEL_MANIFEST_V1,
        modelVersion = "component-model-research-1",
        artifactDigest = "a".repeat(64),
        taxonomyVersion = "ingredient-taxonomy-1",
        taxonomyDigest = "b".repeat(64),
        preprocessingVersion = "preprocess-1",
        postprocessingVersion = "postprocess-1",
        maskContractVersion = "mask-region-v1",
        calibrationVersion = "mass-calibration-1",
        runtime = "onnx-runtime",
        executionProvider = "cpu",
        compatibilityTier = "research-tier",
        ownership = ownership,
        activation = activation,
        rollbackTargetVersion = rollbackTargetVersion,
        productApprovalRef = refs.productApprovalRef,
        engineeringApprovalRef = refs.engineeringApprovalRef,
        legalApprovalRef = refs.legalApprovalRef,
        corpusRef = refs.corpusRef,
        benchmarkEvidenceRef = refs.benchmarkEvidenceRef,
        rollbackEvidenceRef = refs.rollbackEvidenceRef,
    )
}
