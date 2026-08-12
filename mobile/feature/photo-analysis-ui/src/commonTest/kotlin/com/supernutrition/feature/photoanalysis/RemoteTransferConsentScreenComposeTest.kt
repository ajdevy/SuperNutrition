package com.supernutrition.feature.photoanalysis

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.photoanalysis.RemoteTransferDisclosure
import com.supernutrition.domain.photoanalysis.RemoteTransferGate
import com.supernutrition.domain.photoanalysis.RemoteTransferUnavailable
import com.supernutrition.domain.photoanalysis.TransferConsentDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The consent surface as rendered.
 *
 * The forbidden-default test is the first one: `remoteConsent` is listed in
 * `mobile/config/quality/interaction-policy.toml` under `forbidden_defaults`, so
 * the surface must emit nothing at all until a person presses something.
 */
@OptIn(ExperimentalTestApi::class)
class RemoteTransferConsentScreenComposeTest {

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

    private fun askable() = RemoteTransferConsentModel.from(
        RemoteTransferGate.Askable(
            RemoteTransferDisclosure(
                marketJurisdiction = "ZZ",
                processingRegion = "fixture-region-1",
                consentCopyVersion = "photo-transfer-copy-v1",
                retentionVersion = "retention/2",
                retentionDays = 0,
                configurationVersion = "cfg/1",
                locale = "en-GB",
                measurementSystem = "metric",
            ),
        ),
        copy,
    )

    @Test
    fun `rendering the surface decides nothing until someone presses`() {
        // FORBIDDEN DEFAULT: remote consent. Merely showing the surface must not
        // produce a decision, and neither answer may arrive pre-selected.
        var decided: TransferConsentDecision? = null

        runComposeUiTest {
            setContent { RemoteTransferConsentScreen(state = askable(), onDecision = { decided = it }) }

            onNodeWithTag(UiAutomationIds.CONSENT_SURFACE.value).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.CONSENT_GRANT.value).assertIsDisplayed()
            onNodeWithTag(UiAutomationIds.CONSENT_DECLINE.value).assertIsDisplayed()
        }

        assertNull(decided, "the surface decided on the user's behalf")
    }

    @Test
    fun `the market and processing region are on screen before any answer is offered`() {
        runComposeUiTest {
            setContent { RemoteTransferConsentScreen(state = askable(), onDecision = {}) }

            onNodeWithTag(UiAutomationIds.CONSENT_MARKET.value).assertTextContains("ZZ")
            onNodeWithTag(UiAutomationIds.CONSENT_PROCESSING_REGION.value)
                .assertTextContains("fixture-region-1")
            onNodeWithTag(UiAutomationIds.CONSENT_COPY_VERSION.value)
                .assertTextContains("photo-transfer-copy-v1")
            onNodeWithTag(UiAutomationIds.CONSENT_RETENTION.value).assertTextContains("Not kept")
            onNodeWithTag(UiAutomationIds.CONSENT_LOCALE.value).assertTextContains("en-GB · metric")
        }
    }

    @Test
    fun `granting reports granted and declining reports declined`() {
        var decided: TransferConsentDecision? = null

        runComposeUiTest {
            setContent { RemoteTransferConsentScreen(state = askable(), onDecision = { decided = it }) }
            onNodeWithTag(UiAutomationIds.CONSENT_GRANT.value).performClick()
        }
        assertEquals(TransferConsentDecision.GRANTED, decided)

        decided = null
        runComposeUiTest {
            setContent { RemoteTransferConsentScreen(state = askable(), onDecision = { decided = it }) }
            onNodeWithTag(UiAutomationIds.CONSENT_DECLINE.value).performClick()
        }
        assertEquals(TransferConsentDecision.DECLINED, decided)
    }

    @Test
    fun `an unavailable market states the reason instead of hiding the option`() {
        runComposeUiTest {
            val state = RemoteTransferConsentModel.from(
                RemoteTransferGate.Unavailable(RemoteTransferUnavailable.MARKET_UNRESOLVED),
                copy,
            )
            setContent { RemoteTransferConsentScreen(state = state, onDecision = {}) }

            onNodeWithTag(UiAutomationIds.CONSENT_UNAVAILABLE.value)
                .assertTextContains(copy.unavailableMarketUnresolved)
            // No answer may be offered when none may be given.
            onNodeWithTag(UiAutomationIds.CONSENT_GRANT.value).assertDoesNotExist()
            onNodeWithTag(UiAutomationIds.CONSENT_DECLINE.value).assertDoesNotExist()
        }
    }
}
