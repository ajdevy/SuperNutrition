package com.supernutrition.domain.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ComponentModelActivationTest {
    @Test
    fun `active model state rejects a non-production manifest`() {
        assertFailsWith<IllegalArgumentException> {
            ComponentModelActivationController(
                RecordingOperations(),
                initialActive = manifest("research-model", null, ComponentModelActivation.RESEARCH_ONLY),
            )
        }
    }

    @Test
    fun `staging and activation require a verified production manifest`() {
        val operations = RecordingOperations()
        val controller = ComponentModelActivationController(operations)
        val production = manifest("model-1", "model-0")

        assertIs<ComponentModelActivationResult.Staged>(controller.stage(production))
        val activated = assertIs<ComponentModelActivationResult.Activated>(controller.activate(production))

        assertEquals(production, activated.snapshot.active)
        assertEquals(listOf("model-1"), operations.installed)
        assertEquals(2, operations.verificationCount)
    }

    @Test
    fun `unverified artifact is rejected before install and preserves state`() {
        val operations = RecordingOperations(
            check = ComponentModelArtifactCheck.Rejected(
                ComponentModelActivationFailure.ARTIFACT_UNVERIFIED,
            ),
        )
        val controller = ComponentModelActivationController(operations)
        val result = assertIs<ComponentModelActivationResult.Rejected>(
            controller.stage(manifest("model-1", null, ComponentModelActivation.RESEARCH_ONLY)),
        )

        assertEquals(ComponentModelActivationFailure.ARTIFACT_UNVERIFIED, result.failure)
        assertEquals(ComponentModelActivationSnapshot(null, null), result.snapshot)
        assertTrue(operations.installed.isEmpty())
    }

    @Test
    fun `activation rejects an unstaged or non-production manifest`() {
        val controller = ComponentModelActivationController(RecordingOperations())
        val research = manifest("model-1", null, ComponentModelActivation.RESEARCH_ONLY)
        val production = manifest("model-1", "model-0")

        assertEquals(
            ComponentModelActivationFailure.PRODUCTION_APPROVAL_REQUIRED,
            assertIs<ComponentModelActivationResult.Rejected>(controller.activate(research)).failure,
        )
        assertEquals(
            ComponentModelActivationFailure.MANIFEST_MUST_BE_STAGED,
            assertIs<ComponentModelActivationResult.Rejected>(controller.activate(production)).failure,
        )
    }

    @Test
    fun `failed installation leaves the previous active model intact`() {
        val operations = RecordingOperations(installSucceeds = false)
        val initial = manifest("model-0", "model-legacy")
        val controller = ComponentModelActivationController(operations, initialActive = initial)
        val next = manifest("model-1", "model-0")

        controller.stage(next)
        val result = assertIs<ComponentModelActivationResult.Rejected>(controller.activate(next))

        assertEquals(ComponentModelActivationFailure.INSTALL_FAILED, result.failure)
        assertEquals(initial, result.snapshot.active)
        assertEquals(next, result.snapshot.staged)
        assertFalse(operations.installed.contains("model-1"))
    }

    @Test
    fun `rollback installs the declared production target`() {
        val operations = RecordingOperations()
        val initial = manifest("model-0", "model-legacy")
        val controller = ComponentModelActivationController(operations, initialActive = initial)
        val next = manifest("model-1", "model-0")

        controller.stage(next)
        controller.activate(next)
        val result = assertIs<ComponentModelActivationResult.RolledBack>(controller.rollback())

        assertEquals(initial, result.snapshot.active)
        assertEquals(listOf("model-1", "model-0"), operations.installed)
    }

    @Test
    fun `rollback failure preserves the active model`() {
        val operations = RecordingOperations()
        val initial = manifest("model-0", "model-legacy")
        val controller = ComponentModelActivationController(operations, initialActive = initial)
        val next = manifest("model-1", "model-0")

        controller.stage(next)
        controller.activate(next)
        operations.installSucceeds = false
        val result = assertIs<ComponentModelActivationResult.Rejected>(controller.rollback())

        assertEquals(ComponentModelActivationFailure.ROLLBACK_FAILED, result.failure)
        assertEquals(next, result.snapshot.active)
    }

    private class RecordingOperations(
        private val check: ComponentModelArtifactCheck = ComponentModelArtifactCheck.Verified,
        var installSucceeds: Boolean = true,
    ) : ComponentModelArtifactOperations {
        val installed = mutableListOf<String>()
        var verificationCount = 0

        override fun verify(manifest: ComponentModelManifest): ComponentModelArtifactCheck {
            verificationCount += 1
            return check
        }

        override fun installAtomically(manifest: ComponentModelManifest): Boolean {
            if (installSucceeds) installed += manifest.modelVersion
            return installSucceeds
        }
    }

    private fun manifest(
        modelVersion: String,
        rollbackTargetVersion: String?,
        activation: ComponentModelActivation = ComponentModelActivation.PRODUCTION,
    ) = ComponentModelManifest(
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
        ownership = ComponentModelOwnership.APP_MANAGED,
        activation = activation,
        rollbackTargetVersion = rollbackTargetVersion,
        productApprovalRef = if (activation == ComponentModelActivation.PRODUCTION) "approval:product" else null,
        engineeringApprovalRef = if (activation == ComponentModelActivation.PRODUCTION) {
            "approval:engineering"
        } else {
            null
        },
        legalApprovalRef = if (activation == ComponentModelActivation.PRODUCTION) "approval:legal" else null,
        corpusRef = if (activation == ComponentModelActivation.PRODUCTION) "corpus:weighed-v1" else null,
        benchmarkEvidenceRef = if (activation == ComponentModelActivation.PRODUCTION) "evidence:g3-v1" else null,
        rollbackEvidenceRef = if (activation == ComponentModelActivation.PRODUCTION) {
            "evidence:rollback-v1"
        } else {
            null
        },
    )
}
