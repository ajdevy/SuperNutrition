package com.supernutrition.domain.inference

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ComponentModelActivationTest {
    @Test
    fun `staging and activation require a verified production manifest`() {
        runBlocking {
            val operations = RecordingOperations()
            val controller = ComponentModelActivationController(operations)
            val production = manifest("model-1", "model-0")

            assertIs<ComponentModelActivationResult.Staged>(controller.stage(production))
            val activated = assertIs<ComponentModelActivationResult.Activated>(controller.activate(production))

            assertEquals(production, activated.snapshot.active)
            assertEquals(listOf("model-1"), operations.installed)
            assertEquals(2, operations.verificationCount)
        }
    }

    @Test
    fun `unverified artifact is rejected before install and preserves state`() {
        runBlocking {
            val operations = RecordingOperations(
                check = ComponentModelArtifactCheck.Rejected(
                    ComponentModelActivationFailure.ARTIFACT_UNVERIFIED,
                ),
            )
            val controller = ComponentModelActivationController(operations)
            val result = assertIs<ComponentModelActivationResult.Rejected>(
                controller.stage(manifest("model-1", "model-0", ComponentModelActivation.PRODUCTION)),
            )

            assertEquals(ComponentModelActivationFailure.ARTIFACT_UNVERIFIED, result.failure)
            assertEquals(ComponentModelActivationSnapshot(null, null), result.snapshot)
            assertTrue(operations.installed.isEmpty())
        }
    }

    @Test
    fun `activation rejects an unstaged manifest`() {
        runBlocking {
            val controller = ComponentModelActivationController(RecordingOperations())
            val unstaged = manifest("model-1", "model-0", ComponentModelActivation.PRODUCTION)
            val staged = manifest("model-1", "model-0")

            assertEquals(
                ComponentModelActivationFailure.MANIFEST_MUST_BE_STAGED,
                assertIs<ComponentModelActivationResult.Rejected>(controller.activate(unstaged)).failure,
            )
            assertIs<ComponentModelActivationResult.Staged>(controller.stage(staged))
            assertIs<ComponentModelActivationResult.Activated>(controller.activate(staged))
        }
    }

    @Test
    fun `failed installation leaves the previous active model intact`() {
        runBlocking {
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
    }

    @Test
    fun `rollback installs the declared production target`() {
        runBlocking {
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
    }

    @Test
    fun `rollback failure preserves the active model`() {
        runBlocking {
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
    }

    @Test
    fun `concurrent staging serializes verification and preserves the first candidate`() {
        runBlocking {
            val verificationStarted = CompletableDeferred<Unit>()
            val allowVerification = CompletableDeferred<Unit>()
            val controller = ComponentModelActivationController(
                object : ComponentModelArtifactOperations {
                    override suspend fun verify(manifest: ComponentModelManifest): ComponentModelArtifactCheck {
                        verificationStarted.complete(Unit)
                        allowVerification.await()
                        return ComponentModelArtifactCheck.Verified
                    }

                    override suspend fun installAtomically(manifest: ComponentModelManifest): Boolean = true
                },
            )
            val first = manifest("model-1", "model-0")
            val second = manifest("model-2", "model-1")

            val firstStage = async { controller.stage(first) }
            verificationStarted.await()
            val secondStage = async { controller.stage(second) }
            yield()

            assertTrue(secondStage.isActive)
            allowVerification.complete(Unit)
            assertIs<ComponentModelActivationResult.Staged>(firstStage.await())
            assertEquals(
                ComponentModelActivationFailure.MANIFEST_MUST_BE_STAGED,
                assertIs<ComponentModelActivationResult.Rejected>(secondStage.await()).failure,
            )
            assertEquals(ComponentModelActivationSnapshot(null, first), controller.snapshot())
        }
    }

    private class RecordingOperations(
        private val check: ComponentModelArtifactCheck = ComponentModelArtifactCheck.Verified,
        var installSucceeds: Boolean = true,
    ) : ComponentModelArtifactOperations {
        val installed = mutableListOf<String>()
        var verificationCount = 0

        override suspend fun verify(manifest: ComponentModelManifest): ComponentModelArtifactCheck {
            verificationCount += 1
            return check
        }

        override suspend fun installAtomically(manifest: ComponentModelManifest): Boolean {
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
