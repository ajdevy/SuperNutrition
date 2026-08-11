package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InferenceModelBranchCoverageTest {
    @Test
    fun `manifest accepts staged platform lifecycle and preserves provenance route`() {
        val staged = manifest(
            modelVersion = "component-model-staged",
            activation = ComponentModelActivation.STAGED,
            rollbackTargetVersion = "component-model-previous",
        )
        val platformManaged = manifest(
            modelVersion = "component-model-platform",
            activation = ComponentModelActivation.STAGED,
            ownership = ComponentModelOwnership.PLATFORM_MANAGED,
            rollbackTargetVersion = null,
        )

        assertEquals(ComponentModelActivation.STAGED, staged.activation)
        assertEquals("component-model-previous", staged.rollbackTargetVersion)
        assertEquals(ComponentModelOwnership.PLATFORM_MANAGED, platformManaged.ownership)
        assertEquals(
            InferenceV2Provenance(
                route = InferenceRoute.REMOTE,
                engineId = "remote-component-engine",
                modelVersion = staged.modelVersion,
                taxonomyVersion = staged.taxonomyVersion,
                calibrationVersion = staged.calibrationVersion,
                contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
            ),
            staged.toProvenance("remote-component-engine", InferenceRoute.REMOTE),
        )
    }

    @Test
    fun `activation rejects verification and rollback precondition failures without mutating state`() {
        assertRollbackWithoutActiveModel()
        assertRollbackWithUnregisteredTarget()
        assertRejectsIncompatibleActivation()
        assertRejectsIncompatibleRollback()
    }

    @Test
    fun `activation rejects duplicate staging non-production activation and a staged rollback target`() {
        val operations = ScriptedOperations()
        val controller = ComponentModelActivationController(operations)
        val staged = manifest("staged-model", activation = ComponentModelActivation.STAGED)

        assertIs<ComponentModelActivationResult.Staged>(controller.stage(staged))
        assertRejected(
            expected = ComponentModelActivationFailure.MANIFEST_MUST_BE_STAGED,
            actual = controller.stage(staged),
        )
        assertRejected(
            expected = ComponentModelActivationFailure.PRODUCTION_APPROVAL_REQUIRED,
            actual = controller.activate(staged),
        )

        val rollbackController = ComponentModelActivationController(
            operations = ScriptedOperations(),
            initialActive = productionManifest("active-model", rollbackTargetVersion = "staged-target"),
        )
        val stagedTarget = manifest("staged-target", activation = ComponentModelActivation.STAGED)
        assertIs<ComponentModelActivationResult.Staged>(rollbackController.stage(stagedTarget))
        assertRejected(
            expected = ComponentModelActivationFailure.ROLLBACK_TARGET_NOT_PRODUCTION,
            actual = rollbackController.rollback(),
        )
    }

    @Test
    fun `activation controller refuses a non-production initial active manifest`() {
        assertFailsWith<IllegalArgumentException> {
            ComponentModelActivationController(
                operations = ScriptedOperations(),
                initialActive = manifest("staged-active", activation = ComponentModelActivation.STAGED),
            )
        }
    }

    private fun assertRollbackWithoutActiveModel() {
        val noActive = ComponentModelActivationController(ScriptedOperations())
        assertRejected(
            expected = ComponentModelActivationFailure.ROLLBACK_TARGET_UNAVAILABLE,
            actual = noActive.rollback(),
        )
    }

    private fun assertRollbackWithUnregisteredTarget() {
        val unregisteredTarget = ComponentModelActivationController(
            ScriptedOperations(),
            initialActive = productionManifest("active-model", rollbackTargetVersion = "missing-model"),
        )
        assertRejected(
            expected = ComponentModelActivationFailure.MODEL_NOT_REGISTERED,
            actual = unregisteredTarget.rollback(),
        )
    }

    private fun assertRejectsIncompatibleActivation() {
        val activateOperations = ScriptedOperations(
            checks = listOf(
                ComponentModelArtifactCheck.Verified,
                ComponentModelArtifactCheck.Rejected(ComponentModelActivationFailure.INCOMPATIBLE_ARTIFACT),
            ),
        )
        val activateController = ComponentModelActivationController(activateOperations)
        val candidate = productionManifest("candidate-model", rollbackTargetVersion = "previous-model")
        assertIs<ComponentModelActivationResult.Staged>(activateController.stage(candidate))
        val failedActivation = assertRejected(
            expected = ComponentModelActivationFailure.INCOMPATIBLE_ARTIFACT,
            actual = activateController.activate(candidate),
        )
        assertEquals(ComponentModelActivationSnapshot(null, candidate), failedActivation.snapshot)
        assertTrue(activateOperations.installed.isEmpty())
        assertEquals(2, activateOperations.verifyCalls)
    }

    private fun assertRejectsIncompatibleRollback() {
        val rollbackOperations = ScriptedOperations(
            checks = listOf(
                ComponentModelArtifactCheck.Verified,
                ComponentModelArtifactCheck.Rejected(ComponentModelActivationFailure.INCOMPATIBLE_ARTIFACT),
            ),
        )
        val rollbackController = ComponentModelActivationController(
            rollbackOperations,
            initialActive = productionManifest("active-model", rollbackTargetVersion = "previous-model"),
        )
        val previous = productionManifest("previous-model", rollbackTargetVersion = "older-model")
        assertIs<ComponentModelActivationResult.Staged>(rollbackController.stage(previous))
        val failedRollback = assertRejected(
            expected = ComponentModelActivationFailure.INCOMPATIBLE_ARTIFACT,
            actual = rollbackController.rollback(),
        )
        assertEquals("active-model", failedRollback.snapshot.active?.modelVersion)
        assertEquals(previous, failedRollback.snapshot.staged)
        assertTrue(rollbackOperations.installed.isEmpty())
        assertEquals(2, rollbackOperations.verifyCalls)
    }

    @Test
    fun `text terminal outcomes and copied component lists preserve v2 model semantics`() {
        val provenance = InferenceV2ContractFixtures.estimatedPhotoResult.outcome
            .let { assertIs<InferenceV2Outcome.Components>(it).components.single().provenance }
        val textAbstention = InferenceV2Result(
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
            inputModality = InferenceInputModality.TEXT,
            outcome = InferenceV2Outcome.Abstention(
                reason = V2AbstentionReason.NO_FOOD_DETECTED,
                userMessageKey = "inference.abstention.no_food_detected",
                provenance = provenance,
            ),
        )
        val textFailure = InferenceV2Result(
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
            inputModality = InferenceInputModality.TEXT,
            outcome = InferenceV2Outcome.Failure(
                code = V2InferenceFailureCode.INVALID_INPUT,
                retryable = false,
                userMessageKey = "inference.failure.invalid_input",
                provenance = provenance,
            ),
        )

        assertEquals(InferenceInputModality.TEXT, textAbstention.inputModality)
        assertEquals(InferenceInputModality.TEXT, textFailure.inputModality)

        val component = (InferenceV2ContractFixtures.estimatedPhotoResult.outcome as InferenceV2Outcome.Components)
            .components
            .single()
        val callerComponents = mutableListOf(component)
        val stableComponents = InferenceV2Outcome.Components(callerComponents)
        callerComponents.clear()

        assertEquals(listOf(component), stableComponents.components)
        assertEquals(stableComponents, stableComponents.copy())
        assertEquals(stableComponents.hashCode(), stableComponents.copy().hashCode())
        assertEquals("Components(components=[$component])", stableComponents.toString())
    }

    @Test
    fun `v2 confidence and normalized region boundaries fail closed`() {
        assertEquals(ConfidenceBand.LOW, CalibratedConfidence(0.0, ConfidenceBand.LOW).band)
        assertEquals(ConfidenceBand.MEDIUM, CalibratedConfidence(0.5, ConfidenceBand.MEDIUM).band)
        assertEquals(ConfidenceBand.HIGH, CalibratedConfidence(0.8, ConfidenceBand.HIGH).band)
        assertEquals(ConfidenceBand.HIGH, CalibratedConfidence(1.0, ConfidenceBand.HIGH).band)

        val edgeRegion = NormalizedVisibleRegion(0.0, 0.0, 1.0, 1.0)
        assertEquals(1.0, edgeRegion.width)
        assertEquals(1.0, edgeRegion.height)
        assertFailsWith<IllegalArgumentException> { NormalizedVisibleRegion(0.0, -0.1, 0.1, 0.1) }
        assertFailsWith<IllegalArgumentException> { NormalizedVisibleRegion(0.0, 1.1, 0.1, 0.1) }
        assertFailsWith<IllegalArgumentException> { NormalizedVisibleRegion(0.0, 0.0, 0.1, 0.0) }
        assertFailsWith<IllegalArgumentException> { NormalizedVisibleRegion(0.0, 0.8, 0.1, 0.3) }
    }

    @Test
    fun `v2 text components accept non-visual non-mass evidence and reject non-finite inputs`() {
        val textOutcome = InferenceV2ContractFixtures.textNotEvaluableResult.outcome
        val textComponent = (textOutcome as InferenceV2Outcome.Components).components.single()
        val textResult = InferenceV2Result(
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
            inputModality = InferenceInputModality.TEXT,
            outcome = InferenceV2Outcome.Components(
                listOf(textComponent, textComponent.copy(componentId = "text-component-2")),
            ),
        )

        assertEquals(InferenceInputModality.TEXT, textResult.inputModality)
        assertEquals(2, (textResult.outcome as InferenceV2Outcome.Components).components.size)
        assertFailsWith<IllegalArgumentException> {
            CalibratedConfidence(Double.NaN, ConfidenceBand.LOW)
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedVisibleRegion(Double.NaN, 0.0, 0.1, 0.1)
        }
    }

    @Test
    fun `v2 capability rejects ready and unavailable reason mismatches`() {
        val ready = InferenceV2ContractFixtures.readyCapability
        val unavailable = ready.copy(
            state = CapabilityState.MODEL_REQUIRED,
            reason = CapabilityReason.MODEL_MISSING,
        )

        assertEquals(CapabilityState.MODEL_REQUIRED, unavailable.state)
        assertFailsWith<IllegalArgumentException> {
            ready.copy(reason = CapabilityReason.MODEL_MISSING)
        }
        assertFailsWith<IllegalArgumentException> {
            ready.copy(state = CapabilityState.MODEL_REQUIRED, reason = CapabilityReason.NONE)
        }
    }

    @Test
    fun `v1 capability requires a concrete ready model and an unavailable reason`() {
        val common = mapOf(
            "contractVersion" to PHOTO_INFERENCE_CONTRACT_V1,
            "userMessageKey" to "inference.capability",
            "engineId" to "local-engine",
        )

        assertFailsWith<IllegalArgumentException> {
            EngineCapability(
                contractVersion = common.getValue("contractVersion"),
                state = CapabilityState.READY,
                reason = CapabilityReason.NONE,
                userMessageKey = common.getValue("userMessageKey"),
                engineId = common.getValue("engineId"),
                modelVersion = null,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EngineCapability(
                contractVersion = common.getValue("contractVersion"),
                state = CapabilityState.READY,
                reason = CapabilityReason.NONE,
                userMessageKey = common.getValue("userMessageKey"),
                engineId = common.getValue("engineId"),
                modelVersion = " ",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            EngineCapability(
                contractVersion = common.getValue("contractVersion"),
                state = CapabilityState.MODEL_REQUIRED,
                reason = CapabilityReason.NONE,
                userMessageKey = common.getValue("userMessageKey"),
                engineId = common.getValue("engineId"),
            )
        }
    }

    private fun assertRejected(
        expected: ComponentModelActivationFailure,
        actual: ComponentModelActivationResult,
    ): ComponentModelActivationResult.Rejected {
        val rejected = assertIs<ComponentModelActivationResult.Rejected>(actual)
        assertEquals(expected, rejected.failure)
        return rejected
    }

    private class ScriptedOperations(
        private val checks: List<ComponentModelArtifactCheck> = listOf(ComponentModelArtifactCheck.Verified),
    ) : ComponentModelArtifactOperations {
        var verifyCalls = 0
            private set
        val installed = mutableListOf<String>()

        override fun verify(manifest: ComponentModelManifest): ComponentModelArtifactCheck {
            verifyCalls += 1
            return checks.getOrElse(verifyCalls - 1) { checks.last() }
        }

        override fun installAtomically(manifest: ComponentModelManifest): Boolean {
            installed += manifest.modelVersion
            return true
        }
    }

    private fun productionManifest(modelVersion: String, rollbackTargetVersion: String?): ComponentModelManifest =
        manifest(
            modelVersion = modelVersion,
            activation = ComponentModelActivation.PRODUCTION,
            rollbackTargetVersion = rollbackTargetVersion,
            evidence = ManifestEvidence(
                productApprovalRef = "approval:product",
                engineeringApprovalRef = "approval:engineering",
                legalApprovalRef = "approval:legal",
                corpusRef = "corpus:weighed-v1",
                benchmarkEvidenceRef = "evidence:g3-v1",
                rollbackEvidenceRef = "evidence:rollback-v1",
            ),
        )

    private fun manifest(
        modelVersion: String,
        activation: ComponentModelActivation = ComponentModelActivation.PRODUCTION,
        ownership: ComponentModelOwnership = ComponentModelOwnership.APP_MANAGED,
        rollbackTargetVersion: String? = null,
        evidence: ManifestEvidence = ManifestEvidence(),
    ): ComponentModelManifest = ComponentModelManifest(
        manifestVersion = COMPONENT_MODEL_MANIFEST_V1,
        modelVersion = modelVersion,
        artifactDigest = "a".repeat(64),
        taxonomyVersion = "ingredient-taxonomy-1",
        taxonomyDigest = "b".repeat(64),
        preprocessingVersion = "preprocess-1",
        postprocessingVersion = "postprocess-1",
        maskContractVersion = "mask-region-v1",
        calibrationVersion = "mass-calibration-1",
        runtime = "onnx-runtime",
        executionProvider = "cpu",
        compatibilityTier = "high",
        ownership = ownership,
        activation = activation,
        rollbackTargetVersion = rollbackTargetVersion,
        productApprovalRef = evidence.productApprovalRef,
        engineeringApprovalRef = evidence.engineeringApprovalRef,
        legalApprovalRef = evidence.legalApprovalRef,
        corpusRef = evidence.corpusRef,
        benchmarkEvidenceRef = evidence.benchmarkEvidenceRef,
        rollbackEvidenceRef = evidence.rollbackEvidenceRef,
    )

    private data class ManifestEvidence(
        val productApprovalRef: String? = null,
        val engineeringApprovalRef: String? = null,
        val legalApprovalRef: String? = null,
        val corpusRef: String? = null,
        val benchmarkEvidenceRef: String? = null,
        val rollbackEvidenceRef: String? = null,
    )
}
