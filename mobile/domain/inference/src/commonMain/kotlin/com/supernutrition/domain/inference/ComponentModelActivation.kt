package com.supernutrition.domain.inference

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A verifier/install boundary owned by the platform adapter, not the domain. */
interface ComponentModelArtifactOperations {
    /**
     * Verifies a candidate without blocking the caller's UI dispatcher. The platform adapter
     * chooses the appropriate execution context for any file or runtime work it needs to do.
     */
    suspend fun verify(manifest: ComponentModelManifest): ComponentModelArtifactCheck

    /** Installs the already-verified artifact as one atomic operation. */
    suspend fun installAtomically(manifest: ComponentModelManifest): Boolean
}

sealed interface ComponentModelArtifactCheck {
    data object Verified : ComponentModelArtifactCheck

    data class Rejected(val failure: ComponentModelActivationFailure) : ComponentModelArtifactCheck
}

enum class ComponentModelActivationFailure {
    ARTIFACT_UNVERIFIED,
    INCOMPATIBLE_ARTIFACT,
    INSTALL_FAILED,
    MANIFEST_MUST_BE_STAGED,
    PRODUCTION_APPROVAL_REQUIRED,
    MODEL_NOT_REGISTERED,
    ROLLBACK_TARGET_UNAVAILABLE,
    ROLLBACK_TARGET_NOT_PRODUCTION,
    ROLLBACK_FAILED,
}

data class ComponentModelActivationSnapshot(val active: ComponentModelManifest?, val staged: ComponentModelManifest?)

sealed interface ComponentModelActivationResult {
    val snapshot: ComponentModelActivationSnapshot

    data class Staged(override val snapshot: ComponentModelActivationSnapshot) : ComponentModelActivationResult

    data class Activated(override val snapshot: ComponentModelActivationSnapshot) : ComponentModelActivationResult

    data class RolledBack(override val snapshot: ComponentModelActivationSnapshot) : ComponentModelActivationResult

    data class Rejected(
        val failure: ComponentModelActivationFailure,
        override val snapshot: ComponentModelActivationSnapshot,
    ) : ComponentModelActivationResult
}

/**
 * Serial model selection state machine. It owns no bytes and cannot activate an
 * unverified artifact. A failed install or rollback leaves the active model
 * untouched so callers can continue using the last known-good version.
 */
class ComponentModelActivationController(
    private val operations: ComponentModelArtifactOperations,
    initialActive: ComponentModelManifest? = null,
) {
    private var active: ComponentModelManifest? = initialActive.also { manifest ->
        require(manifest == null || manifest.activation == ComponentModelActivation.PRODUCTION) {
            "The active component model must be production-approved"
        }
    }
    private var staged: ComponentModelManifest? = null
    private val registered = mutableMapOf<String, ComponentModelManifest>()
    private val stateMutex = Mutex()

    init {
        initialActive?.let { registered[it.modelVersion] = it }
    }

    suspend fun snapshot(): ComponentModelActivationSnapshot = stateMutex.withLock { snapshotLocked() }

    /** Verifies and records a candidate; it cannot change the active model. */
    suspend fun stage(manifest: ComponentModelManifest): ComponentModelActivationResult = stateMutex.withLock {
        if (staged != null) {
            return@withLock rejectedLocked(ComponentModelActivationFailure.MANIFEST_MUST_BE_STAGED)
        }
        when (val check = operations.verify(manifest)) {
            ComponentModelArtifactCheck.Verified -> {
                registered[manifest.modelVersion] = manifest
                staged = manifest
                ComponentModelActivationResult.Staged(snapshotLocked())
            }

            is ComponentModelArtifactCheck.Rejected -> rejectedLocked(check.failure)
        }
    }

    /** Activates only an exact, previously staged production manifest. */
    suspend fun activate(manifest: ComponentModelManifest): ComponentModelActivationResult = stateMutex.withLock {
        if (manifest.activation != ComponentModelActivation.PRODUCTION) {
            return@withLock rejectedLocked(ComponentModelActivationFailure.PRODUCTION_APPROVAL_REQUIRED)
        }
        if (staged != manifest) {
            return@withLock rejectedLocked(ComponentModelActivationFailure.MANIFEST_MUST_BE_STAGED)
        }
        when (val check = operations.verify(manifest)) {
            ComponentModelArtifactCheck.Verified -> {
                if (!operations.installAtomically(manifest)) {
                    rejectedLocked(ComponentModelActivationFailure.INSTALL_FAILED)
                } else {
                    registered[manifest.modelVersion] = manifest
                    active = manifest
                    staged = null
                    ComponentModelActivationResult.Activated(snapshotLocked())
                }
            }

            is ComponentModelArtifactCheck.Rejected -> rejectedLocked(check.failure)
        }
    }

    /** Restores the active manifest's declared production rollback target. */
    suspend fun rollback(): ComponentModelActivationResult = stateMutex.withLock {
        val current = active
            ?: return@withLock rejectedLocked(ComponentModelActivationFailure.ROLLBACK_TARGET_UNAVAILABLE)
        val targetVersion = current.rollbackTargetVersion
            ?: return@withLock rejectedLocked(ComponentModelActivationFailure.ROLLBACK_TARGET_UNAVAILABLE)
        val target = registered[targetVersion]
            ?: return@withLock rejectedLocked(ComponentModelActivationFailure.MODEL_NOT_REGISTERED)
        if (target.activation != ComponentModelActivation.PRODUCTION) {
            return@withLock rejectedLocked(ComponentModelActivationFailure.ROLLBACK_TARGET_NOT_PRODUCTION)
        }
        when (val check = operations.verify(target)) {
            ComponentModelArtifactCheck.Verified -> {
                if (!operations.installAtomically(target)) {
                    rejectedLocked(ComponentModelActivationFailure.ROLLBACK_FAILED)
                } else {
                    active = target
                    staged = null
                    ComponentModelActivationResult.RolledBack(snapshotLocked())
                }
            }

            is ComponentModelArtifactCheck.Rejected -> rejectedLocked(check.failure)
        }
    }

    private fun snapshotLocked() = ComponentModelActivationSnapshot(active, staged)

    private fun rejectedLocked(failure: ComponentModelActivationFailure): ComponentModelActivationResult.Rejected =
        ComponentModelActivationResult.Rejected(failure, snapshotLocked())
}
