package com.supernutrition.core.config

/** Immutable context captured once for one analysis attempt. */
data class ConfigurationSnapshot(
    val snapshotId: String,
    val revision: Long,
    val defaultLocale: String,
    val measurementSystem: String,
    val catalogCountry: String,
    val marketJurisdiction: String,
    val processingRegion: String,
    val policyVersion: String,
    val digest: String,
) {
    init {
        require(snapshotId.isNotBlank())
        require(revision >= 0)
        require(defaultLocale.isNotBlank())
        require(measurementSystem.isNotBlank())
        require(catalogCountry.isNotBlank())
        require(marketJurisdiction.isNotBlank())
        require(processingRegion.isNotBlank())
        require(policyVersion.isNotBlank())
        require(digest.matches(DIGEST_PATTERN)) { "Snapshot digest must be SHA-256" }
    }

    private companion object {
        val DIGEST_PATTERN = Regex("[a-f0-9]{64}")
    }
}

enum class DefaultableField {
    DEFAULT_LOCALE,
    MEASUREMENT_SYSTEM,
    CATALOG_PROPOSAL,
    STANDARD_SERVING,
}

enum class DefaultProvenance {
    BUNDLED_SAFE_DEFAULT,
    GLOBAL_RELEASE,
    CATALOG_CONFIRMED,
}

data class AtomicServing(val unit: String, val amount: String) {
    init {
        require(unit.isNotBlank())
        require(amount.isNotBlank())
    }
}

data class CatalogServingProposal(val canonicalItemId: String, val serving: AtomicServing, val sourceId: String) {
    init {
        require(canonicalItemId.isNotBlank())
        require(sourceId.isNotBlank())
    }
}

data class DefaultResolutionInput(
    val field: DefaultableField,
    val snapshot: ConfigurationSnapshot,
    val canonicalItemConfirmed: Boolean = false,
    val catalogProposal: CatalogServingProposal? = null,
)

sealed interface DefaultResolution {
    data class Accepted(
        val field: DefaultableField,
        val value: String,
        val provenance: DefaultProvenance,
        val snapshotDigest: String,
    ) : DefaultResolution

    data class Rejected(val field: DefaultableField, val reason: RejectionReason) : DefaultResolution
}

enum class RejectionReason {
    CATALOG_ITEM_NOT_CONFIRMED,
    MISSING_CATALOG_PROPOSAL,
    FORBIDDEN_FIELD,
}

/**
 * Projects an attempt's snapshot into the context interaction defaults read.
 *
 * Renamed from `MarketPolicyResolver`, which it never was: it performs no policy
 * lookup, consults no approver, and checks no expiry. `MarketPolicyRegistry` in
 * MarketPolicy.kt is the contract-backed resolver AR-53 names.
 */
class PolicyContextResolver {
    fun resolve(snapshot: ConfigurationSnapshot): PolicyContext = PolicyContext(
        catalogCountry = snapshot.catalogCountry,
        marketJurisdiction = snapshot.marketJurisdiction,
        processingRegion = snapshot.processingRegion,
        locale = snapshot.defaultLocale,
    )
}

data class PolicyContext(
    val catalogCountry: String,
    val marketJurisdiction: String,
    val processingRegion: String,
    val locale: String,
)

/** Resolves only the closed allowlist and never emits remote consent or identity. */
class DefaultResolver {
    fun resolve(input: DefaultResolutionInput): DefaultResolution = when (input.field) {
        DefaultableField.DEFAULT_LOCALE -> accepted(input, input.snapshot.defaultLocale)

        DefaultableField.MEASUREMENT_SYSTEM -> accepted(input, input.snapshot.measurementSystem)

        DefaultableField.CATALOG_PROPOSAL -> when {
            !input.canonicalItemConfirmed ->
                DefaultResolution.Rejected(input.field, RejectionReason.CATALOG_ITEM_NOT_CONFIRMED)

            input.catalogProposal == null ->
                DefaultResolution.Rejected(input.field, RejectionReason.MISSING_CATALOG_PROPOSAL)

            else -> accepted(input, input.catalogProposal.canonicalItemId, DefaultProvenance.CATALOG_CONFIRMED)
        }

        DefaultableField.STANDARD_SERVING -> when {
            !input.canonicalItemConfirmed ->
                DefaultResolution.Rejected(input.field, RejectionReason.CATALOG_ITEM_NOT_CONFIRMED)

            input.catalogProposal == null ->
                DefaultResolution.Rejected(input.field, RejectionReason.MISSING_CATALOG_PROPOSAL)

            else -> accepted(
                input,
                "${input.catalogProposal.serving.unit}:${input.catalogProposal.serving.amount}",
                DefaultProvenance.CATALOG_CONFIRMED,
            )
        }
    }

    private fun accepted(
        input: DefaultResolutionInput,
        value: String,
        provenance: DefaultProvenance = DefaultProvenance.BUNDLED_SAFE_DEFAULT,
    ) = DefaultResolution.Accepted(
        field = input.field,
        value = value,
        provenance = provenance,
        snapshotDigest = input.snapshot.digest,
    )
}
