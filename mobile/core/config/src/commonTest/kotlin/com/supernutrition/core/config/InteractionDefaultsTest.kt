package com.supernutrition.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class InteractionDefaultsTest {
    private val snapshot = ConfigurationSnapshot(
        snapshotId = "attempt-1",
        revision = 1,
        defaultLocale = "en-US",
        measurementSystem = "metric",
        catalogCountry = "US",
        marketJurisdiction = "US",
        processingRegion = "us-east",
        policyVersion = "v1",
        digest = "a".repeat(64),
    )

    @Test
    fun `snapshot resolves context without letting locale choose region`() {
        val context = MarketPolicyResolver().resolve(snapshot)

        assertEquals("US", context.catalogCountry)
        assertEquals("US", context.marketJurisdiction)
        assertEquals("us-east", context.processingRegion)
        assertEquals("en-US", context.locale)
    }

    @Test
    fun `standard serving is rejected until canonical item is confirmed`() {
        val resolution = DefaultResolver().resolve(
            DefaultResolutionInput(
                field = DefaultableField.STANDARD_SERVING,
                snapshot = snapshot,
                catalogProposal = CatalogServingProposal(
                    canonicalItemId = "food:apple",
                    serving = AtomicServing("g", "100"),
                    sourceId = "catalog-v1",
                ),
            ),
        )

        assertEquals(
            RejectionReason.CATALOG_ITEM_NOT_CONFIRMED,
            assertIs<DefaultResolution.Rejected>(resolution).reason,
        )
    }

    @Test
    fun `allowlisted snapshot defaults resolve with provenance`() {
        val resolver = DefaultResolver()

        val locale = assertIs<DefaultResolution.Accepted>(
            resolver.resolve(
                DefaultResolutionInput(DefaultableField.DEFAULT_LOCALE, snapshot),
            ),
        )
        assertEquals("en-US", locale.value)
        assertEquals(DefaultProvenance.BUNDLED_SAFE_DEFAULT, locale.provenance)

        val measurement = assertIs<DefaultResolution.Accepted>(
            resolver.resolve(
                DefaultResolutionInput(DefaultableField.MEASUREMENT_SYSTEM, snapshot),
            ),
        )
        assertEquals("metric", measurement.value)
        assertEquals(DefaultProvenance.BUNDLED_SAFE_DEFAULT, measurement.provenance)
    }

    @Test
    fun `catalog proposal is rejected when the proposal is missing`() {
        val resolution = DefaultResolver().resolve(
            DefaultResolutionInput(
                field = DefaultableField.CATALOG_PROPOSAL,
                snapshot = snapshot,
                canonicalItemConfirmed = true,
            ),
        )

        assertEquals(
            RejectionReason.MISSING_CATALOG_PROPOSAL,
            assertIs<DefaultResolution.Rejected>(resolution).reason,
        )
    }

    @Test
    fun `confirmed catalog proposal resolves with provenance`() {
        val resolution = DefaultResolver().resolve(
            DefaultResolutionInput(
                field = DefaultableField.CATALOG_PROPOSAL,
                snapshot = snapshot,
                canonicalItemConfirmed = true,
                catalogProposal = CatalogServingProposal(
                    canonicalItemId = "food:apple",
                    serving = AtomicServing("g", "100"),
                    sourceId = "catalog-v1",
                ),
            ),
        )

        val accepted = assertIs<DefaultResolution.Accepted>(resolution)
        assertEquals("food:apple", accepted.value)
        assertEquals(DefaultProvenance.CATALOG_CONFIRMED, accepted.provenance)
    }

    @Test
    fun `standard serving is rejected when the confirmed catalog serving is missing`() {
        val resolution = DefaultResolver().resolve(
            DefaultResolutionInput(
                field = DefaultableField.STANDARD_SERVING,
                snapshot = snapshot,
                canonicalItemConfirmed = true,
            ),
        )

        assertEquals(
            RejectionReason.MISSING_CATALOG_PROPOSAL,
            assertIs<DefaultResolution.Rejected>(resolution).reason,
        )
    }

    @Test
    fun `catalog proposal is rejected until canonical item is confirmed`() {
        val resolution = DefaultResolver().resolve(
            DefaultResolutionInput(
                field = DefaultableField.CATALOG_PROPOSAL,
                snapshot = snapshot,
                catalogProposal = CatalogServingProposal(
                    canonicalItemId = "food:apple",
                    serving = AtomicServing("g", "100"),
                    sourceId = "catalog-v1",
                ),
            ),
        )

        assertEquals(
            RejectionReason.CATALOG_ITEM_NOT_CONFIRMED,
            assertIs<DefaultResolution.Rejected>(resolution).reason,
        )
    }

    @Test
    fun `configuration and catalog value objects reject malformed inputs`() {
        val invalidSnapshots = listOf(
            "snapshotId" to { snapshot.copy(snapshotId = "") },
            "revision" to { snapshot.copy(revision = -1) },
            "defaultLocale" to { snapshot.copy(defaultLocale = "") },
            "measurementSystem" to { snapshot.copy(measurementSystem = "") },
            "catalogCountry" to { snapshot.copy(catalogCountry = "") },
            "marketJurisdiction" to { snapshot.copy(marketJurisdiction = "") },
            "processingRegion" to { snapshot.copy(processingRegion = "") },
            "policyVersion" to { snapshot.copy(policyVersion = "") },
            "digest" to { snapshot.copy(digest = "invalid") },
        )
        invalidSnapshots.forEach { (field, createInvalid) ->
            assertFailsWith<IllegalArgumentException>("Invalid $field must be rejected") { createInvalid() }
        }
        assertFailsWith<IllegalArgumentException> { AtomicServing("", "100") }
        assertFailsWith<IllegalArgumentException> { AtomicServing("g", "") }
        assertFailsWith<IllegalArgumentException> {
            CatalogServingProposal("", AtomicServing("g", "100"), "catalog-v1")
        }
        assertFailsWith<IllegalArgumentException> {
            CatalogServingProposal("food:apple", AtomicServing("g", "100"), "")
        }
    }

    @Test
    fun `confirmed serving is atomic and carries catalog provenance`() {
        val resolution = DefaultResolver().resolve(
            DefaultResolutionInput(
                field = DefaultableField.STANDARD_SERVING,
                snapshot = snapshot,
                canonicalItemConfirmed = true,
                catalogProposal = CatalogServingProposal(
                    canonicalItemId = "food:apple",
                    serving = AtomicServing("g", "100"),
                    sourceId = "catalog-v1",
                ),
            ),
        )

        val accepted = assertIs<DefaultResolution.Accepted>(resolution)
        assertEquals("g:100", accepted.value)
        assertEquals(DefaultProvenance.CATALOG_CONFIRMED, accepted.provenance)
    }

    @Test
    fun `decision ledger supports accept override undo without persistence`() {
        val ledger = DefaultDecisionLedger(snapshot.digest, "b".repeat(64))
        val proposal = DefaultProposal(
            proposalId = "proposal-1",
            field = DefaultableField.MEASUREMENT_SYSTEM,
            value = "metric",
            provenance = DefaultProvenance.BUNDLED_SAFE_DEFAULT,
            snapshotDigest = snapshot.digest,
            policyDigest = "b".repeat(64),
            revision = 1,
        )

        val accepted = assertIs<DefaultDecision.Accepted>(ledger.accept(proposal, "decision-1"))
        assertEquals("metric", accepted.value)
        assertEquals(accepted, ledger.accept(proposal, "decision-1"))

        val overrideProposal = proposal.copy(revision = 2)
        val overridden = assertIs<DefaultDecision.Overridden>(
            ledger.override(overrideProposal, "decision-2", "imperial"),
        )
        assertEquals("imperial", overridden.value)
        assertFailsWith<IllegalArgumentException> {
            ledger.override(overrideProposal, "decision-2", "metric")
        }

        val undoProposal = overrideProposal.copy(revision = 3)
        val undone = assertIs<DefaultDecision.Undone>(
            ledger.undo(undoProposal, "decision-3", "decision-2"),
        )
        assertEquals("decision-2", undone.undoneDecisionId)
        assertEquals(3, ledger.decisions().size)
    }

    @Test
    fun `decision ledger rejects stale digest revision reuse and invalidation resurrection`() {
        val ledger = DefaultDecisionLedger(snapshot.digest, "b".repeat(64))
        val proposal = DefaultProposal(
            proposalId = "proposal-2",
            field = DefaultableField.DEFAULT_LOCALE,
            value = "en-US",
            provenance = DefaultProvenance.BUNDLED_SAFE_DEFAULT,
            snapshotDigest = snapshot.digest,
            policyDigest = "b".repeat(64),
            revision = 1,
        )

        ledger.accept(proposal, "decision-4")
        assertFailsWith<IllegalArgumentException> {
            ledger.accept(proposal, "decision-5")
        }
        assertFailsWith<IllegalArgumentException> {
            ledger.accept(proposal.copy(snapshotDigest = "c".repeat(64), revision = 2), "decision-6")
        }

        val invalidated = ledger.invalidate(proposal.copy(revision = 2), "decision-7", "catalog changed")
        assertIs<DefaultDecision.Invalidated>(invalidated)
        assertFailsWith<IllegalArgumentException> {
            ledger.override(proposal.copy(revision = 3), "decision-8", "fr-FR")
        }
    }

    @Test
    fun `decision ledger rejects invalid operations and protects decision identity`() {
        assertFailsWith<IllegalArgumentException> { DefaultDecisionLedger("invalid", "b".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { DefaultDecisionLedger("a".repeat(64), "invalid") }

        val ledger = DefaultDecisionLedger(snapshot.digest, "b".repeat(64))
        val proposal = DefaultProposal(
            proposalId = "proposal-invalid-ops",
            field = DefaultableField.DEFAULT_LOCALE,
            value = "en-US",
            provenance = DefaultProvenance.BUNDLED_SAFE_DEFAULT,
            snapshotDigest = snapshot.digest,
            policyDigest = "b".repeat(64),
            revision = 1,
        )
        assertFailsWith<IllegalArgumentException> { ledger.accept(proposal, "") }
        assertFailsWith<IllegalArgumentException> { ledger.override(proposal, "override-1", "") }
        assertFailsWith<IllegalArgumentException> {
            ledger.undo(proposal, "undo-1", "unknown")
        }
        assertFailsWith<IllegalArgumentException> {
            ledger.invalidate(proposal, "invalidate-1", "")
        }

        val accepted = ledger.accept(proposal, "decision-identity")
        val otherProposal = proposal.copy(proposalId = "other", revision = 1)
        assertFailsWith<IllegalArgumentException> {
            ledger.accept(otherProposal, "decision-identity")
        }
        ledger.accept(otherProposal, "foreign-decision")
        assertFailsWith<IllegalArgumentException> {
            ledger.undo(proposal.copy(revision = 2), "undo-foreign", "foreign-decision")
        }
        assertFailsWith<IllegalArgumentException> {
            ledger.accept(proposal.copy(policyDigest = "c".repeat(64), revision = 2), "policy-mismatch")
        }
        assertEquals(accepted, ledger.accept(proposal, "decision-identity"))
        assertFailsWith<IllegalArgumentException> {
            ledger.accept(proposal.copy(revision = 2), "decision-identity")
        }
    }

    @Test
    fun `proposal validation rejects blank identity and negative revision`() {
        val valid = DefaultProposal(
            proposalId = "proposal-valid",
            field = DefaultableField.DEFAULT_LOCALE,
            value = "en-US",
            provenance = DefaultProvenance.BUNDLED_SAFE_DEFAULT,
            snapshotDigest = snapshot.digest,
            policyDigest = "b".repeat(64),
            revision = 0,
        )
        assertFailsWith<IllegalArgumentException> { valid.copy(proposalId = "") }
        assertFailsWith<IllegalArgumentException> { valid.copy(value = "") }
        assertFailsWith<IllegalArgumentException> { valid.copy(snapshotDigest = "invalid") }
        assertFailsWith<IllegalArgumentException> { valid.copy(policyDigest = "invalid") }
        assertFailsWith<IllegalArgumentException> { valid.copy(revision = -1) }
    }
}
