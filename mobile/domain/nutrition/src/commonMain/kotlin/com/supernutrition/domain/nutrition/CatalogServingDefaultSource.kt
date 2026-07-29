package com.supernutrition.domain.nutrition

import com.supernutrition.core.config.CatalogServingProposal
import com.supernutrition.core.config.ConfigurationSnapshot
import com.supernutrition.domain.inference.requireNonBlankValue
import com.supernutrition.domain.inference.requireSha256Digest

/** A confirmed canonical item bound to the immutable attempt snapshot. */
data class CanonicalItemConfirmation(
    val canonicalItemId: String,
    val confirmationId: String,
    val snapshotDigest: String,
) {
    init {
        requireNonBlankValue(canonicalItemId, "Canonical item ID must not be blank")
        requireNonBlankValue(confirmationId, "Confirmation ID must not be blank")
        requireSha256Digest(snapshotDigest, "Snapshot digest must be a lowercase SHA-256 digest")
    }
}

/**
 * Domain port for the only catalog-derived default allowed by the MVP.
 * The port exposes only a read-only snapshot and one atomic value. Its API has
 * no mutation or persistence operation, so implementations cannot persist a
 * correction through this contract.
 */
fun interface CatalogServingDefaultSource {
    fun standardServing(
        snapshot: ConfigurationSnapshot,
        confirmation: CanonicalItemConfirmation,
    ): CatalogServingProposal?
}
