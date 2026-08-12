package com.supernutrition.domain.photoanalysis

import com.supernutrition.core.config.MarketId
import com.supernutrition.core.config.MarketPolicy
import com.supernutrition.core.config.MarketPolicyRegistry
import com.supernutrition.core.config.RemotePermission
import com.supernutrition.core.config.ResidencyPosture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Consent for a remote transfer, held to `contracts/mobile-api/v1/consent-receipt.toml`.
 *
 * Slice A synthesised a granted receipt. Every assertion here exists because the
 * synthesised version would have passed without it: it was granted, it had a
 * market, and it had a copy version — none of which the user had seen.
 */
class RemoteTransferConsentTest {

    private val attempt = "att_0000000000000001"
    private val digest = "sha256:" + "a".repeat(64)

    // The fictional market from contracts/market-policy/v1/fixtures. A real
    // jurisdiction here would be a legal position published by a test.
    private fun fixtureMarket(
        photoPermission: RemotePermission = RemotePermission.PERMITTED,
        regions: List<String> = listOf("fixture-region-1"),
        expiresOnEpochDay: Long = 20_000,
    ) = MarketPolicy(
        marketId = MarketId("ZZ"),
        jurisdiction = "fixture-jurisdiction",
        residencyPosture = ResidencyPosture.PERMITTED_WITH_REGION,
        permittedProcessingRegions = regions,
        photoRemotePermission = photoPermission,
        textRemotePermission = RemotePermission.FORBIDDEN,
        photoConsentCopyVersion = "photo-transfer-copy-v1",
        textConsentCopyVersion = null,
        acceptedInputLanguages = listOf("en", "ru"),
        legalReviewApprover = "fixture-approver",
        legalReviewExpiresOnEpochDay = expiresOnEpochDay,
    )

    private fun policy(registry: MarketPolicyRegistry) =
        RemoteTransferConsentPolicy(registry = registry, retentionVersion = "retention/2")

    private fun gate(
        registry: MarketPolicyRegistry,
        copyVersion: String = "photo-transfer-copy-v1",
        marketId: String = "ZZ",
        today: Long = 19_000,
    ) = policy(registry).gate(
        marketId = marketId,
        presentedConsentCopyVersion = copyVersion,
        context = RemoteTransferContext("cfg/1", "en-GB", "metric"),
        todayEpochDay = today,
    )

    // --- the gate -----------------------------------------------------------

    @Test
    fun theShippedRegistryCannotOfferATransferAtAll() {
        // MarketPolicyRegistry.unresolved is empty until a named approver
        // declares a market. The product still works; there is simply no remote
        // step to offer, and the surface must say so rather than omit it.
        val result = gate(MarketPolicyRegistry.unresolved)

        assertEquals(
            RemoteTransferGate.Unavailable(RemoteTransferUnavailable.MARKET_UNRESOLVED),
            result,
        )
    }

    @Test
    fun aDeclaredMarketOffersTheRegionAndCopyItAuthorises() {
        val result = gate(MarketPolicyRegistry(listOf(fixtureMarket())))

        assertTrue(result is RemoteTransferGate.Askable, "expected an askable gate, got $result")
        val disclosure = result.disclosure
        assertEquals("ZZ", disclosure.marketJurisdiction)
        assertEquals("fixture-region-1", disclosure.processingRegion)
        assertEquals("photo-transfer-copy-v1", disclosure.consentCopyVersion)
        assertEquals(0, disclosure.retentionDays)
        // Locale and measurement are shown, not silently defaulted.
        assertEquals("en-GB", disclosure.locale)
        assertEquals("metric", disclosure.measurementSystem)
    }

    @Test
    fun aCopyVersionTheMarketDoesNotAuthoriseCannotBeOffered() {
        // The surface showing one disclosure while the market authorises another
        // is exactly how a photo disclosure could authorise a text transfer.
        val result = gate(MarketPolicyRegistry(listOf(fixtureMarket())), copyVersion = "some-other-copy-v9")

        assertEquals(
            RemoteTransferGate.Unavailable(RemoteTransferUnavailable.CONSENT_COPY_MISMATCH),
            result,
        )
    }

    @Test
    fun aMarketThatForbidsPhotoTransferCannotBeOffered() {
        val registry = MarketPolicyRegistry(
            listOf(
                fixtureMarket(photoPermission = RemotePermission.FORBIDDEN)
                    .copy(photoConsentCopyVersion = null),
            ),
        )

        assertEquals(
            RemoteTransferGate.Unavailable(RemoteTransferUnavailable.MODALITY_FORBIDDEN),
            gate(registry),
        )
    }

    @Test
    fun aLapsedLegalReviewIsTheSameAnswerAsNoMarketAtAll() {
        val registry = MarketPolicyRegistry(listOf(fixtureMarket(expiresOnEpochDay = 18_000)))

        assertEquals(
            RemoteTransferGate.Unavailable(RemoteTransferUnavailable.MARKET_UNRESOLVED),
            gate(registry, today = 19_000),
        )
    }

    @Test
    fun aMarketIdThatIsNotTwoLettersIsUnresolvedRatherThanLookedUp() {
        assertEquals(
            RemoteTransferGate.Unavailable(RemoteTransferUnavailable.MARKET_UNRESOLVED),
            gate(MarketPolicyRegistry(listOf(fixtureMarket())), marketId = "fixture-jurisdiction"),
        )
    }

    // --- the receipt --------------------------------------------------------

    private fun receipt(
        decision: TransferConsentDecision = TransferConsentDecision.GRANTED,
        processingRegion: String? = "fixture-region-1",
        consentCopyVersion: String? = "photo-transfer-copy-v1",
        attemptId: String = attempt,
        overrides: ReceiptOverrides = ReceiptOverrides(),
    ) = TransferConsentReceipt(
        version = "1",
        attemptId = attemptId,
        digest = overrides.digest ?: digest,
        modality = "photo",
        purpose = "food-identification",
        decision = decision,
        marketJurisdiction = "ZZ",
        processingRegion = processingRegion,
        consentCopyVersion = consentCopyVersion,
        retentionVersion = "retention/2",
        retentionDays = overrides.retentionDays,
        configurationVersion = "cfg/1",
        recordedAt = "2026-08-05T10:00:00Z",
        expiresAt = "2026-08-05T10:10:00Z",
    )

    /** The two rarely-varied fields, so the helper stays under the parameter limit. */
    private data class ReceiptOverrides(val digest: String? = null, val retentionDays: Int = 0)

    @Test
    fun aGrantedReceiptMustRecordWhatTheUserWasShown() {
        // Without these there is no record of what was disclosed, which is the
        // one thing the receipt exists to prove.
        assertFailsWith<IllegalArgumentException> { receipt(processingRegion = null) }
        assertFailsWith<IllegalArgumentException> { receipt(consentCopyVersion = null) }
        assertFailsWith<IllegalArgumentException> { receipt(processingRegion = "") }
    }

    @Test
    fun aDeclinedReceiptIsRecordableWithNeitherRegionNorCopy() {
        // Proving a user said no is as important as proving they said yes, and a
        // refusal before any region resolved has neither to record.
        val declined = receipt(
            decision = TransferConsentDecision.DECLINED,
            processingRegion = null,
            consentCopyVersion = null,
        )

        assertEquals(TransferConsentDecision.DECLINED, declined.decision)
        assertNull(declined.processingRegion)
    }

    @Test
    fun aReceiptRejectsAnAttemptIdOrDigestTheGatewayWouldRefuse() {
        assertFailsWith<IllegalArgumentException> { receipt(attemptId = "android-photo-attempt") }
        assertFailsWith<IllegalArgumentException> {
            receipt(overrides = ReceiptOverrides(digest = "a".repeat(64)))
        }
        assertFailsWith<IllegalArgumentException> {
            receipt(
                overrides = ReceiptOverrides(digest = "sha256:not-a-digest"),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            receipt(
                overrides = ReceiptOverrides(digest = "sha256:" + "A".repeat(64)),
            )
        }
    }

    @Test
    fun v1CannotExpressAnyProviderSideRetention() {
        // A nonzero value has to be a visible contract change, not a
        // configuration edit.
        assertFailsWith<IllegalArgumentException> { receipt(overrides = ReceiptOverrides(retentionDays = 1)) }
    }

    // --- the store ----------------------------------------------------------

    @Test
    fun aDecisionAuthorisesOnlyTheAttemptAndBytesItWasMadeFor() {
        val store = AttemptScopedConsentStore()
        store.record(attempt, receipt())

        assertTrue(store.isGrantedFor(attempt, digest))
        // Different bytes: the payload changed under the decision.
        assertTrue(!store.isGrantedFor(attempt, "sha256:" + "b".repeat(64)))
        // Different attempt: a receipt is not reusable.
        assertTrue(!store.isGrantedFor("att_0000000000000002", digest))
    }

    @Test
    fun aDeclinedDecisionNeverAuthorisesATransfer() {
        val store = AttemptScopedConsentStore()
        store.record(
            attempt,
            receipt(
                decision = TransferConsentDecision.DECLINED,
                processingRegion = null,
                consentCopyVersion = null,
            ),
        )

        assertTrue(!store.isGrantedFor(attempt, digest))
        // ...but the refusal itself is recorded and findable.
        assertEquals(TransferConsentDecision.DECLINED, store.receiptFor(attempt, digest)?.decision)
    }

    @Test
    fun anEmptyStoreAuthorisesNothing() {
        val store = AttemptScopedConsentStore()

        assertTrue(!store.isGrantedFor(attempt, digest))
        assertNull(store.receiptFor(attempt, digest))
    }

    @Test
    fun recordingASecondAttemptDiscardsTheFirst() {
        // attempt-scoped, per contracts/interaction-policy/v1: a decision that
        // outlived its attempt would authorise bytes the user never saw.
        val store = AttemptScopedConsentStore()
        store.record(attempt, receipt())
        store.record("att_0000000000000002", receipt(attemptId = "att_0000000000000002"))

        assertTrue(!store.isGrantedFor(attempt, digest))
        assertTrue(store.isGrantedFor("att_0000000000000002", digest))

        store.clear()
        assertTrue(!store.isGrantedFor("att_0000000000000002", digest))
    }

    // --- every rejection the disclosure and receipt state ------------------

    private fun disclosure(
        marketJurisdiction: String = "ZZ",
        processingRegion: String = "fixture-region-1",
        consentCopyVersion: String = "photo-transfer-copy-v1",
        retentionDays: Int = 0,
        blanked: String? = null,
    ) = RemoteTransferDisclosure(
        marketJurisdiction = marketJurisdiction,
        processingRegion = processingRegion,
        consentCopyVersion = consentCopyVersion,
        retentionVersion = if (blanked == "retentionVersion") "" else "retention/2",
        retentionDays = retentionDays,
        configurationVersion = if (blanked == "configurationVersion") "" else "cfg/1",
        locale = if (blanked == "locale") "" else "en-GB",
        measurementSystem = if (blanked == "measurementSystem") "" else "metric",
    )

    @Test
    fun aDisclosureRefusesToShowNothingWhereAFactBelongs() {
        // Each of these renders as a labelled row with no content — a disclosure
        // that discloses nothing while still inviting a decision.
        assertFailsWith<IllegalArgumentException> { disclosure(marketJurisdiction = "zz") }
        assertFailsWith<IllegalArgumentException> { disclosure(marketJurisdiction = "ZZZ") }
        assertFailsWith<IllegalArgumentException> { disclosure(processingRegion = "") }
        assertFailsWith<IllegalArgumentException> { disclosure(consentCopyVersion = "") }
        assertFailsWith<IllegalArgumentException> { disclosure(blanked = "retentionVersion") }
        assertFailsWith<IllegalArgumentException> { disclosure(retentionDays = 1) }
        assertFailsWith<IllegalArgumentException> { disclosure(blanked = "configurationVersion") }
        assertFailsWith<IllegalArgumentException> { disclosure(blanked = "locale") }
        assertFailsWith<IllegalArgumentException> { disclosure(blanked = "measurementSystem") }

        disclosure()
    }

    private fun receiptWith(
        version: String = "1",
        modality: String = "photo",
        purpose: String = "food-identification",
        market: String = "ZZ",
        blanked: String? = null,
    ) = TransferConsentReceipt(
        version = version,
        attemptId = attempt,
        digest = digest,
        modality = modality,
        purpose = purpose,
        decision = TransferConsentDecision.GRANTED,
        marketJurisdiction = market,
        processingRegion = "fixture-region-1",
        consentCopyVersion = "photo-transfer-copy-v1",
        retentionVersion = if (blanked == "retentionVersion") "" else "retention/2",
        retentionDays = 0,
        configurationVersion = if (blanked == "configurationVersion") "" else "cfg/1",
        recordedAt = if (blanked == "recordedAt") "" else "2026-08-05T10:00:00Z",
        expiresAt = if (blanked == "expiresAt") "" else "2026-08-05T10:10:00Z",
    )

    @Test
    fun aReceiptRefusesEveryShapeTheGatewayWouldReject() {
        assertFailsWith<IllegalArgumentException> { receiptWith(version = "2") }
        assertFailsWith<IllegalArgumentException> { receiptWith(modality = "text") }
        assertFailsWith<IllegalArgumentException> { receiptWith(purpose = "marketing") }
        assertFailsWith<IllegalArgumentException> { receiptWith(market = "zz") }
        assertFailsWith<IllegalArgumentException> { receiptWith(blanked = "retentionVersion") }
        assertFailsWith<IllegalArgumentException> { receiptWith(blanked = "configurationVersion") }
        assertFailsWith<IllegalArgumentException> { receiptWith(blanked = "recordedAt") }
        assertFailsWith<IllegalArgumentException> { receiptWith(blanked = "expiresAt") }

        receiptWith()
    }

    @Test
    fun aMarketPermittedWithNoRegionCannotBeOffered() {
        val registry = MarketPolicyRegistry(listOf(fixtureMarket(regions = emptyList())))

        assertEquals(
            RemoteTransferGate.Unavailable(RemoteTransferUnavailable.NO_PERMITTED_REGION),
            gate(registry),
        )
    }

    @Test
    fun theStoreKnowsWhetherAnAttemptWasAskedAtAll() {
        // Distinct from "may this transfer proceed": a decline is a decision, and
        // re-asking someone who already said no would be its own defect.
        val store = AttemptScopedConsentStore()

        assertTrue(!store.decidedFor(attempt, digest))

        store.record(attempt, receipt())
        assertTrue(store.decidedFor(attempt, digest))
        assertTrue(!store.decidedFor("att_0000000000000002", digest))

        store.clear()
        assertTrue(!store.decidedFor(attempt, digest))
    }

    @Test
    fun `a second photo is asked about again rather than inheriting the first answer`() {
        // P2 — the Android host uses one constant attempt id for every capture,
        // so an attempt-only check stayed true after the first decision and the
        // consent surface was never shown again. The engine's digest binding
        // meant the transfer then refused rather than sending the wrong thing,
        // but the user was never asked, and a decision nobody was offered is not
        // consent.
        val store = AttemptScopedConsentStore()
        val first = "sha256:" + "a".repeat(64)
        val second = "sha256:" + "b".repeat(64)

        store.record(attempt, receipt(overrides = ReceiptOverrides(digest = first)))

        assertTrue(store.decidedFor(attempt, first), "the answered photo should be decided")
        assertTrue(!store.decidedFor(attempt, second), "a new photo inherited the old answer")
    }

    @Test
    fun `re-answering for the new photo decides it without disturbing the binding`() {
        val store = AttemptScopedConsentStore()
        val second = "sha256:" + "b".repeat(64)

        store.record(attempt, receipt(overrides = ReceiptOverrides(digest = "sha256:" + "a".repeat(64))))
        store.record(attempt, receipt(overrides = ReceiptOverrides(digest = second)))

        assertTrue(store.decidedFor(attempt, second))
        assertEquals(second, store.receiptFor(attempt, second)?.digest)
    }
}
