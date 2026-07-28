package com.supernutrition.domain.nutrition

import com.supernutrition.core.config.CatalogServingProposal
import com.supernutrition.core.config.ConfigurationSnapshot

/** A confirmed canonical item bound to the immutable attempt snapshot. */
data class CanonicalItemConfirmation(
    val canonicalItemId: String,
    val confirmationId: String,
    val snapshotDigest: String,
) {
    init {
        require(canonicalItemId.isNotBlank())
        require(confirmationId.isNotBlank())
        require(snapshotDigest.matches(DIGEST_PATTERN))
    }

    private companion object {
        val DIGEST_PATTERN = Regex("[a-f0-9]{64}")
    }
}

/**
 * Domain port for the only catalog-derived default allowed by the MVP.
 * Implementations return one atomic unit/amount pair and never mutate the
 * supplied configuration snapshot or persist a user correction.
 */
fun interface CatalogServingDefaultSource {
    fun standardServing(
        snapshot: ConfigurationSnapshot,
        confirmation: CanonicalItemConfirmation,
    ): CatalogServingProposal?
}
