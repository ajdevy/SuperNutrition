package com.supernutrition.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * Story E1.6's acceptance criteria, as tests.
 *
 * Each one is a way a processing position could widen without anyone deciding to
 * widen it. They are worth writing as tests rather than trusting to the contract
 * verifier because the verifier checks the declared data and these check the code
 * that reads it — a market could be declared correctly and still resolved wrongly.
 */
class MarketPolicyTest {
    private val today = 20_000L

    // A base value plus copy() rather than a many-argument factory: MarketPolicy
    // is a data class whose init re-runs on copy, so each test states only the
    // one field it is about and still exercises every invariant.
    private val base = MarketPolicy(
        marketId = MarketId("ZZ"),
        jurisdiction = "fixture-jurisdiction",
        residencyPosture = ResidencyPosture.PERMITTED_WITH_REGION,
        permittedProcessingRegions = listOf("region-1"),
        photoRemotePermission = RemotePermission.PERMITTED,
        textRemotePermission = RemotePermission.FORBIDDEN,
        photoConsentCopyVersion = "photo-copy-v1",
        textConsentCopyVersion = null,
        acceptedInputLanguages = listOf("en", "ru"),
        legalReviewApprover = "fixture-approver",
        legalReviewExpiresOnEpochDay = today + 365,
    )

    // An unresolved market is allowed to have no live approval; every other
    // posture must have one, or the type refuses it.
    private fun expiryFor(posture: ResidencyPosture): Long =
        if (posture == ResidencyPosture.UNRESOLVED) 0 else today + 365

    @Test
    fun `the shipped registry declares no market until a named approver supplies one`() {
        assertEquals(emptyList(), MarketPolicyRegistry.unresolved.policies)
    }

    @Test
    fun `absent, declared unresolved, and expired are the same answer`() {
        // The equivalence the whole contract turns on. If these differed, a
        // market could acquire a weaker form of permission by lapsing.
        val registry = MarketPolicyRegistry(
            listOf(
                base.copy(
                    marketId = MarketId("AA"),
                    residencyPosture = ResidencyPosture.UNRESOLVED,
                    photoRemotePermission = RemotePermission.FORBIDDEN,
                    photoConsentCopyVersion = null,
                ),
                base.copy(marketId = MarketId("BB"), legalReviewExpiresOnEpochDay = today),
            ),
        )

        assertIs<MarketResolution.Unresolved>(registry.resolve(MarketId("XX"), today))
        assertEquals(
            UnresolvedReason.ABSENT,
            (registry.resolve(MarketId("XX"), today) as MarketResolution.Unresolved).reason,
        )
        assertEquals(
            UnresolvedReason.DECLARED_UNRESOLVED,
            (registry.resolve(MarketId("AA"), today) as MarketResolution.Unresolved).reason,
        )
        assertEquals(
            UnresolvedReason.REVIEW_EXPIRED,
            (registry.resolve(MarketId("BB"), today) as MarketResolution.Unresolved).reason,
        )
    }

    @Test
    fun `an unresolved market refuses transfer for both modalities, at the transfer path`() {
        // "Fails closed at the transfer path, not merely in the interface" --
        // so the refusal is asserted on mayTransfer, not on a UI state.
        for (modality in TransferModality.entries) {
            val decision = MarketPolicyRegistry.unresolved
                .mayTransfer(MarketId("XX"), modality, "any-copy", today)

            assertEquals(TransferRefusal.MARKET_UNRESOLVED, assertIs<TransferDecision.Refused>(decision).reason)
        }
    }

    @Test
    fun `an expired approval stops permitting what it permitted yesterday`() {
        val registry = MarketPolicyRegistry(listOf(base.copy(legalReviewExpiresOnEpochDay = today + 1)))

        assertIs<TransferDecision.Permitted>(
            registry.mayTransfer(MarketId("ZZ"), TransferModality.PHOTO, "photo-copy-v1", today),
        )
        assertEquals(
            TransferRefusal.MARKET_UNRESOLVED,
            assertIs<TransferDecision.Refused>(
                registry.mayTransfer(MarketId("ZZ"), TransferModality.PHOTO, "photo-copy-v1", today + 1),
            ).reason,
        )
    }

    @Test
    fun `a market permitting photo but not text rejects the text transfer`() {
        val registry = MarketPolicyRegistry(listOf(base))

        assertIs<TransferDecision.Permitted>(
            registry.mayTransfer(MarketId("ZZ"), TransferModality.PHOTO, "photo-copy-v1", today),
        )
        assertEquals(
            TransferRefusal.MODALITY_FORBIDDEN,
            assertIs<TransferDecision.Refused>(
                registry.mayTransfer(MarketId("ZZ"), TransferModality.TEXT, "photo-copy-v1", today),
            ).reason,
        )
    }

    @Test
    fun `presenting one modality's consent copy for the other fails closed`() {
        // The rule AR-51 states and nothing enforced: the photo disclosure says a
        // smaller copy of the photo is sent, which is false for a typed
        // description, so it cannot authorise one.
        val registry = MarketPolicyRegistry(
            listOf(
                base.copy(
                    textRemotePermission = RemotePermission.PERMITTED,
                    textConsentCopyVersion = "text-copy-v1",
                ),
            ),
        )

        assertIs<TransferDecision.Permitted>(
            registry.mayTransfer(MarketId("ZZ"), TransferModality.TEXT, "text-copy-v1", today),
        )
        assertEquals(
            TransferRefusal.CONSENT_COPY_MISMATCH,
            assertIs<TransferDecision.Refused>(
                registry.mayTransfer(MarketId("ZZ"), TransferModality.TEXT, "photo-copy-v1", today),
            ).reason,
        )
    }

    @Test
    fun `a permitted modality with no region still refuses`() {
        val registry = MarketPolicyRegistry(listOf(base.copy(permittedProcessingRegions = emptyList())))

        assertEquals(
            TransferRefusal.NO_PERMITTED_REGION,
            assertIs<TransferDecision.Refused>(
                registry.mayTransfer(MarketId("ZZ"), TransferModality.PHOTO, "photo-copy-v1", today),
            ).reason,
        )
    }

    @Test
    fun `accepted input languages resolve identically whatever the posture`() {
        // Supporting a language is a product capability; operating in a market is
        // a legal position. Neither implies the other, in either direction.
        val postures = ResidencyPosture.entries.map { posture ->
            MarketPolicyRegistry(
                listOf(
                    base.copy(
                        residencyPosture = posture,
                        photoRemotePermission = RemotePermission.FORBIDDEN,
                        photoConsentCopyVersion = null,
                        legalReviewExpiresOnEpochDay = expiryFor(posture),
                    ),
                ),
            ).acceptedInputLanguages(MarketId("ZZ"))
        }

        assertEquals(List(postures.size) { listOf("en", "ru") }, postures)
    }

    @Test
    fun `a permission with no reviewed copy behind it is a contradiction`() {
        assertFailsWith<IllegalArgumentException> {
            base.copy(textRemotePermission = RemotePermission.PERMITTED, textConsentCopyVersion = null)
        }
        assertFailsWith<IllegalArgumentException> {
            base.copy(photoConsentCopyVersion = "  ")
        }
    }

    @Test
    fun `one consent copy cannot be declared for both modalities`() {
        assertFailsWith<IllegalArgumentException> {
            base.copy(
                textRemotePermission = RemotePermission.PERMITTED,
                photoConsentCopyVersion = "shared-v1",
                textConsentCopyVersion = "shared-v1",
            )
        }
    }

    @Test
    fun `a market with a posture must name its approver and an expiry`() {
        assertFailsWith<IllegalArgumentException> { base.copy(legalReviewApprover = "  ") }
        assertFailsWith<IllegalArgumentException> { base.copy(legalReviewExpiresOnEpochDay = 0) }
    }

    @Test
    fun `one market may not hold two positions`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            MarketPolicyRegistry(listOf(base, base.copy(permittedProcessingRegions = listOf("region-2"))))
        }

        assertEquals(true, failure.message.orEmpty().contains("one reconciled answer"))
    }
}
