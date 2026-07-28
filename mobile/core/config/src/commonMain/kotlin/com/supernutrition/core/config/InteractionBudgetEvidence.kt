package com.supernutrition.core.config

/** Required interaction dimensions; choice is cumulative with tap. */
data class RequiredInteractionCounts(val tap: Int, val scroll: Int, val field: Int, val choice: Int) {
    init {
        require(listOf(tap, scroll, field, choice).all { it >= 0 })
    }
}

/** Bounded auxiliary events that are not ordinary happy-path taps. */
data class AuxiliaryInteractionCounts(
    val textEntryChars: Int,
    val choiceSurface: Int,
    val back: Int,
    val undo: Int,
    val cancel: Int,
    val permissionPrompt: Int,
) {
    init {
        require(
            listOf(textEntryChars, choiceSurface, back, undo, cancel, permissionPrompt)
                .all { it >= 0 },
        )
    }
}

/** Approved maxima for one journey/platform/accessibility evidence record. */
data class InteractionBudgetMaxima(
    val required: RequiredInteractionCounts,
    val auxiliary: AuxiliaryInteractionCounts,
) {
    fun assertContains(observed: InteractionBudgetEvidence) {
        require(observed.required.tap <= required.tap) { "tap budget exceeded" }
        require(observed.required.scroll <= required.scroll) { "scroll budget exceeded" }
        require(observed.required.field <= required.field) { "field budget exceeded" }
        require(observed.required.choice <= required.choice) { "choice budget exceeded" }
        require(observed.auxiliary.textEntryChars <= auxiliary.textEntryChars) {
            "textEntryChars budget exceeded"
        }
        require(observed.auxiliary.choiceSurface <= auxiliary.choiceSurface) {
            "choiceSurface budget exceeded"
        }
        require(observed.auxiliary.back <= auxiliary.back) { "back budget exceeded" }
        require(observed.auxiliary.undo <= auxiliary.undo) { "undo budget exceeded" }
        require(observed.auxiliary.cancel <= auxiliary.cancel) { "cancel budget exceeded" }
        require(observed.auxiliary.permissionPrompt <= auxiliary.permissionPrompt) {
            "permissionPrompt budget exceeded"
        }
    }
}

/**
 * Contract-neutral, privacy-safe interaction evidence. Values and text are
 * represented only by digests; the record is suitable for Android/iOS and
 * Maestro normalization without storing photo or user-entered content.
 */
data class InteractionBudgetEvidence(
    val schemaVersion: String,
    val policyVersion: String,
    val policyDigest: String,
    val configurationDigest: String,
    val proposalId: String?,
    val decisionId: String?,
    val resolvedValueDigest: String?,
    val fieldId: String,
    val sourceIdentity: String,
    val scenarioId: String,
    val platform: String,
    val locale: String,
    val accessibilityMode: String,
    val defaultProvenance: DefaultProvenance?,
    val lifecycleOutcome: String,
    val eventSequence: Long,
    val required: RequiredInteractionCounts,
    val auxiliary: AuxiliaryInteractionCounts,
) {
    init {
        require(schemaVersion.isNotBlank())
        require(policyVersion.isNotBlank())
        require(policyDigest.matches(DIGEST_PATTERN))
        require(configurationDigest.matches(DIGEST_PATTERN))
        require(proposalId == null || proposalId.isNotBlank())
        require(decisionId == null || decisionId.isNotBlank())
        require(resolvedValueDigest == null || resolvedValueDigest.matches(DIGEST_PATTERN))
        require(fieldId.isNotBlank())
        require(sourceIdentity.isNotBlank())
        require(scenarioId.isNotBlank())
        require(platform.isNotBlank())
        require(locale.isNotBlank())
        require(accessibilityMode.isNotBlank())
        require(lifecycleOutcome.isNotBlank())
        require(eventSequence >= 0)
    }

    private companion object {
        val DIGEST_PATTERN = Regex("[a-f0-9]{64}")
    }
}
