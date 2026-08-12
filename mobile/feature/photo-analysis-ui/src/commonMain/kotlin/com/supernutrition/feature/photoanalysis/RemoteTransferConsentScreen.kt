package com.supernutrition.feature.photoanalysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.supernutrition.core.ui.UiAutomationId
import com.supernutrition.core.ui.UiAutomationIds
import com.supernutrition.domain.photoanalysis.TransferConsentDecision

/**
 * The consent surface: everything disclosed, then the two answers.
 *
 * Both answers are `OutlinedButton`s in declaration order granted-then-declined
 * and neither is a filled/primary button. That is the AD-40 requirement made
 * structural rather than stylistic — a filled "Send" beside an outlined "Keep on
 * device" is a pre-selection expressed in emphasis, which is the form this
 * product is most likely to reintroduce by accident. There is no default
 * selection to render because [RemoteTransferConsentState.Askable] has no field
 * for one.
 *
 * The disclosure rows are drawn before the buttons in the same Column, so a
 * decision cannot be reached without the market and processing region having
 * been laid out above it.
 */
@Composable
fun RemoteTransferConsentScreen(
    state: RemoteTransferConsentState,
    onDecision: (TransferConsentDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .automationId(UiAutomationIds.CONSENT_SURFACE),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (state) {
            is RemoteTransferConsentState.Unavailable -> Text(
                text = state.message,
                modifier = Modifier.automationId(UiAutomationIds.CONSENT_UNAVAILABLE),
            )

            is RemoteTransferConsentState.Askable -> {
                Text(text = state.prompt)
                state.disclosureRows.forEach { row -> DisclosureLine(row) }
                ConsentAnswers(state, onDecision)
            }
        }
    }
}

@Composable
private fun DisclosureLine(row: DisclosureRow) {
    // mergeDescendants so the tagged node carries the label AND the value. An
    // unmerged row exposes an empty node under the automation id, which reads as
    // a disclosure that discloses nothing to a test, to UI Automator, and to a
    // screen reader alike.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { testTag = row.labelKey.automationId().value },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = row.label)
        Text(text = row.value)
    }
}

@Composable
private fun ConsentAnswers(state: RemoteTransferConsentState.Askable, onDecision: (TransferConsentDecision) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = { onDecision(TransferConsentDecision.GRANTED) },
            modifier = Modifier.automationId(UiAutomationIds.CONSENT_GRANT),
        ) {
            Text(state.grantLabel)
        }
        OutlinedButton(
            onClick = { onDecision(TransferConsentDecision.DECLINED) },
            modifier = Modifier.automationId(UiAutomationIds.CONSENT_DECLINE),
        ) {
            Text(state.declineLabel)
        }
    }
}

/** Mirrors the capture screen's own private helper; both tag by automation id. */
private fun Modifier.automationId(id: UiAutomationId): Modifier = testTag(id.value)

private fun DisclosureField.automationId() = when (this) {
    DisclosureField.MARKET -> UiAutomationIds.CONSENT_MARKET
    DisclosureField.PROCESSING_REGION -> UiAutomationIds.CONSENT_PROCESSING_REGION
    DisclosureField.RETENTION -> UiAutomationIds.CONSENT_RETENTION
    DisclosureField.COPY_VERSION -> UiAutomationIds.CONSENT_COPY_VERSION
    DisclosureField.LOCALE -> UiAutomationIds.CONSENT_LOCALE
}
