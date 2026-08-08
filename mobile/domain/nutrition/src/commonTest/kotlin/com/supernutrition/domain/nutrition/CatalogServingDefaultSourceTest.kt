package com.supernutrition.domain.nutrition

import com.supernutrition.core.config.AtomicServing
import com.supernutrition.core.config.CatalogServingProposal
import com.supernutrition.core.config.ConfigurationSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogServingDefaultSourceTest {
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
    fun `source returns one atomic serving only for confirmed item`() {
        val source = CatalogServingDefaultSource { currentSnapshot, confirmation ->
            require(currentSnapshot.digest == confirmation.snapshotDigest)
            CatalogServingProposal(
                canonicalItemId = confirmation.canonicalItemId,
                serving = AtomicServing("g", "100"),
                sourceId = "catalog-v1",
            )
        }

        assertEquals(
            CatalogServingProposal(
                canonicalItemId = "food:apple",
                serving = AtomicServing("g", "100"),
                sourceId = "catalog-v1",
            ),
            source.standardServing(
                snapshot,
                CanonicalItemConfirmation("food:apple", "confirmation-1", snapshot.digest),
            ),
        )
    }

    @Test
    fun `confirmation cannot be detached from the attempt snapshot`() {
        assertFailsWith<IllegalArgumentException> {
            CanonicalItemConfirmation("food:apple", "confirmation-1", "not-a-digest")
        }
    }

    @Test
    fun `confirmation requires both opaque identities`() {
        assertFailsWith<IllegalArgumentException> {
            CanonicalItemConfirmation("", "confirmation-1", snapshot.digest)
        }
        assertFailsWith<IllegalArgumentException> {
            CanonicalItemConfirmation("food:apple", "", snapshot.digest)
        }
    }

    @Test
    fun `source may return no default and cannot mutate or persist the snapshot`() {
        val originalSnapshot = snapshot
        var calls = 0
        val source = CatalogServingDefaultSource { currentSnapshot, confirmation ->
            calls += 1
            assertEquals(snapshot, currentSnapshot)
            assertEquals(snapshot.digest, confirmation.snapshotDigest)
            null
        }

        assertEquals(
            null,
            source.standardServing(
                snapshot,
                CanonicalItemConfirmation("food:apple", "confirmation-null", snapshot.digest),
            ),
        )
        assertEquals(1, calls)
        assertEquals(originalSnapshot, snapshot)
    }

    @Test
    fun `source implementations receive a snapshot mismatch as a rejected input`() {
        val source = CatalogServingDefaultSource { _, confirmation ->
            CatalogServingProposal(
                canonicalItemId = confirmation.canonicalItemId,
                serving = AtomicServing("g", "100"),
                sourceId = "catalog-v1",
            )
        }

        val result = source.standardServingIfBoundToSnapshot(
            snapshot,
            CanonicalItemConfirmation("food:apple", "confirmation-match", snapshot.digest),
        )
        assertEquals("food:apple", result?.canonicalItemId)
        val otherAttempt = CanonicalItemConfirmation(
            canonicalItemId = "food:apple",
            confirmationId = "confirmation-other",
            snapshotDigest = "b".repeat(64),
        )
        assertEquals(null, source.standardServingIfBoundToSnapshot(snapshot, otherAttempt))
    }
}
