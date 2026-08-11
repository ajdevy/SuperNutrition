package com.supernutrition.domain.inference

/** A verifier/install boundary owned by the platform adapter, not the domain. */
interface ComponentModelArtifactOperations {
    fun verify(manifest: ComponentModelManifest): ComponentModelArtifactCheck

    /** Installs the already-verified artifact as one atomic operation. */
    fun installAtomically(manifest: ComponentModelManifest): Boolean
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

    init {
        initialActive?.let { registered[it.modelVersion] = it }
    }

    fun snapshot(): ComponentModelActivationSnapshot = ComponentModelActivationSnapshot(active, staged)

    /** Verifies and records a candidate; it cannot change the active model. */
    fun stage(manifest: ComponentModelManifest): ComponentModelActivationResult {
        if (staged != null) {
            return rejected(ComponentModelActivationFailure.MANIFEST_MUST_BE_STAGED)
        }
        return when (val check = operations.verify(manifest)) {
            ComponentModelArtifactCheck.Verified -> {
                registered[manifest.modelVersion] = manifest
                staged = manifest
                ComponentModelActivationResult.Staged(snapshot())
            }

            is ComponentModelArtifactCheck.Rejected -> rejected(check.failure)
        }
    }

    /** Activates only an exact, previously staged production manifest. */
    fun activate(manifest: ComponentModelManifest): ComponentModelActivationResult {
        if (manifest.activation != ComponentModelActivation.PRODUCTION) {
            return rejected(ComponentModelActivationFailure.PRODUCTION_APPROVAL_REQUIRED)
        }
        if (staged != manifest) {
            return rejected(ComponentModelActivationFailure.MANIFEST_MUST_BE_STAGED)
        }
        return when (val check = operations.verify(manifest)) {
            ComponentModelArtifactCheck.Verified -> {
                if (!operations.installAtomically(manifest)) {
                    rejected(ComponentModelActivationFailure.INSTALL_FAILED)
                } else {
                    registered[manifest.modelVersion] = manifest
                    active = manifest
                    staged = null
                    ComponentModelActivationResult.Activated(snapshot())
                }
            }

            is ComponentModelArtifactCheck.Rejected -> rejected(check.failure)
        }
    }

    /** Restores the active manifest's declared production rollback target. */
    fun rollback(): ComponentModelActivationResult {
        val current = active ?: return rejected(ComponentModelActivationFailure.ROLLBACK_TARGET_UNAVAILABLE)
        val targetVersion = current.rollbackTargetVersion
            ?: return rejected(ComponentModelActivationFailure.ROLLBACK_TARGET_UNAVAILABLE)
        val target = registered[targetVersion]
            ?: return rejected(ComponentModelActivationFailure.MODEL_NOT_REGISTERED)
        if (target.activation != ComponentModelActivation.PRODUCTION) {
            return rejected(ComponentModelActivationFailure.ROLLBACK_TARGET_NOT_PRODUCTION)
        }
        return when (val check = operations.verify(target)) {
            ComponentModelArtifactCheck.Verified -> {
                if (!operations.installAtomically(target)) {
                    rejected(ComponentModelActivationFailure.ROLLBACK_FAILED)
                } else {
                    active = target
                    staged = null
                    ComponentModelActivationResult.RolledBack(snapshot())
                }
            }

            is ComponentModelArtifactCheck.Rejected -> rejected(check.failure)
        }
    }

    private fun rejected(failure: ComponentModelActivationFailure): ComponentModelActivationResult.Rejected =
        ComponentModelActivationResult.Rejected(failure, snapshot())
}
