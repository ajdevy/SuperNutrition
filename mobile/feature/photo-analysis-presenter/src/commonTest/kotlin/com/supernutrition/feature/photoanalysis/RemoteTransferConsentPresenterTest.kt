package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.photoanalysis.RemoteTransferDisclosure
import com.supernutrition.domain.photoanalysis.RemoteTransferGate
import com.supernutrition.domain.photoanalysis.RemoteTransferUnavailable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * What the consent surface presents, and what it refuses to present.
 *
 * The rules under test are the ones a plausible implementation gets wrong: a
 * pre-selected answer, a market or region the presenter substituted for the
 * resolved one, and an unavailable market rendered as an absent option instead
 * of a stated one.
 */
class RemoteTransferConsentPresenterTest {

    private val copy = ConsentCopy(
        prompt = "Send this photo for analysis?",
        grant = "Send photo",
        decline = "Keep on device",
        marketLabel = "Market",
        processingRegionLabel = "Processed in",
        retentionLabel = "Kept by provider",
        retentionNone = "Not kept",
        copyVersionLabel = "Disclosure",
        localeLabel = "Language and units",
        unavailableMarketUnresolved = "Remote analysis is not available in your market yet.",
        unavailableMarketProhibited = "prohibited",
        unavailableModalityForbidden = "Photo analysis cannot leave the device in your market.",
        unavailableNoRegion = "No approved processing region is available.",
        unavailableCopyMismatch = "This build cannot show the approved disclosure.",
    )

    private fun disclosure() = RemoteTransferDisclosure(
        marketJurisdiction = "ZZ",
        processingRegion = "fixture-region-1",
        consentCopyVersion = "photo-transfer-copy-v1",
        retentionVersion = "retention/2",
        retentionDays = 0,
        configurationVersion = "cfg/1",
        locale = "en-GB",
        measurementSystem = "metric",
    )

    @Test
    fun anAskableGateShowsWhereTheDataGoesBeforeAnythingElse() {
        val state = RemoteTransferConsentModel.from(RemoteTransferGate.Askable(disclosure()), copy)

        assertTrue(state is RemoteTransferConsentState.Askable, "got $state")
        // Jurisdiction and region are what the contract requires to be visible
        // at decision time, so they lead.
        assertEquals(DisclosureField.MARKET, state.disclosureRows[0].labelKey)
        assertEquals("ZZ", state.disclosureRows[0].value)
        assertEquals(DisclosureField.PROCESSING_REGION, state.disclosureRows[1].labelKey)
        assertEquals("fixture-region-1", state.disclosureRows[1].value)
    }

    @Test
    fun everyDisclosedFactReachesTheSurface() {
        val state = RemoteTransferConsentModel.from(RemoteTransferGate.Askable(disclosure()), copy)
            as RemoteTransferConsentState.Askable

        assertEquals(
            listOf(
                DisclosureField.MARKET,
                DisclosureField.PROCESSING_REGION,
                DisclosureField.RETENTION,
                DisclosureField.COPY_VERSION,
                DisclosureField.LOCALE,
            ),
            state.disclosureRows.map { it.labelKey },
        )
        assertEquals("photo-transfer-copy-v1", state.disclosureRows[3].value)
        // Locale and measurement are shown rather than silently defaulted.
        assertEquals("en-GB · metric", state.disclosureRows[4].value)
    }

    @Test
    fun zeroRetentionIsStatedInWordsRatherThanAsANumber() {
        val state = RemoteTransferConsentModel.from(RemoteTransferGate.Askable(disclosure()), copy)
            as RemoteTransferConsentState.Askable

        assertEquals("Not kept", state.disclosureRows[2].value)
    }

    @Test
    fun bothAnswersAreOfferedAndNeitherIsMarkedAsThePreferredOne() {
        // AD-40: upload and no-upload are equal, unselected and visually
        // neutral. The state type carries no selection field at all, so this
        // asserts the shape as much as the values.
        val state = RemoteTransferConsentModel.from(RemoteTransferGate.Askable(disclosure()), copy)
            as RemoteTransferConsentState.Askable

        assertEquals("Send photo", state.grantLabel)
        assertEquals("Keep on device", state.declineLabel)
        assertEquals("Send this photo for analysis?", state.prompt)
    }

    @Test
    fun eachUnavailableReasonIsStatedRatherThanCollapsed() {
        // A single "unavailable" string would hide a forbidden market behind the
        // same words as an unshippable disclosure, and the two need different
        // follow-ups.
        val messages = RemoteTransferUnavailable.entries.map { reason ->
            val state = RemoteTransferConsentModel.from(RemoteTransferGate.Unavailable(reason), copy)
            assertTrue(state is RemoteTransferConsentState.Unavailable, "got $state for $reason")
            state.message
        }

        assertEquals(messages.size, messages.toSet().size, "each reason needs its own message: $messages")
        assertEquals(copy.unavailableMarketUnresolved, messages.first())
    }

    @Test
    fun consentCopyRefusesToCarryABlankString() {
        // A blank label renders as an unlabelled row, which is a disclosure that
        // discloses nothing.
        assertFailsWith<IllegalArgumentException> { copy.copy(marketLabel = "") }
        assertFailsWith<IllegalArgumentException> { copy.copy(grant = " ") }
    }
}
