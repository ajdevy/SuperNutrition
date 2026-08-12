package com.supernutrition.feature.photoanalysis

import com.supernutrition.domain.photoanalysis.RemoteTransferDisclosure
import com.supernutrition.domain.photoanalysis.RemoteTransferGate
import com.supernutrition.domain.photoanalysis.RemoteTransferUnavailable

/**
 * The consent surface's state, derived from the gate and nothing else.
 *
 * The presenter owns no locale lookup and no policy: it turns a
 * [RemoteTransferGate] into labelled rows, so the market and region the user
 * sees are the ones the policy layer resolved rather than anything this layer
 * chose. That matters because the receipt records what was displayed — if the
 * surface could substitute its own values the receipt would attest to something
 * the user never saw.
 */
sealed interface RemoteTransferConsentState {

    /**
     * A decision can be asked for.
     *
     * There is deliberately no `selected` or `preferred` field. AD-40 forbids a
     * pre-selection, and a model that cannot express one cannot drift into
     * having one — which a boolean defaulting to false would.
     */
    data class Askable(
        val disclosureRows: List<DisclosureRow>,
        val grantLabel: String,
        val declineLabel: String,
        val prompt: String,
    ) : RemoteTransferConsentState

    /** No decision may be asked for; the surface says why rather than hiding. */
    data class Unavailable(val message: String) : RemoteTransferConsentState
}

/** One disclosed fact, with the automation id the surface tags it with. */
data class DisclosureRow(val labelKey: DisclosureField, val label: String, val value: String)

enum class DisclosureField { MARKET, PROCESSING_REGION, RETENTION, COPY_VERSION, LOCALE }

/** Host-supplied copy. Every string is localizable; the presenter formats none of it itself. */
data class ConsentCopy(
    val prompt: String,
    val grant: String,
    val decline: String,
    val marketLabel: String,
    val processingRegionLabel: String,
    val retentionLabel: String,
    val retentionNone: String,
    val copyVersionLabel: String,
    val localeLabel: String,
    val unavailableMarketUnresolved: String,
    /**
     * Distinct copy from "unresolved", because the two say different things to
     * the user: one is "we have not set this up here yet", the other is "this is
     * not permitted here". Reusing the unresolved string would tell someone in a
     * prohibited market to wait for something that is never coming.
     */
    val unavailableMarketProhibited: String,
    val unavailableModalityForbidden: String,
    val unavailableNoRegion: String,
    val unavailableCopyMismatch: String,
) {
    init {
        values.forEach { require(it.isNotBlank()) { "Consent copy must not carry a blank string" } }
    }

    internal val values: List<String>
        get() = listOf(
            prompt, grant, decline, marketLabel, processingRegionLabel, retentionLabel,
            retentionNone, copyVersionLabel, localeLabel, unavailableMarketUnresolved,
            unavailableMarketProhibited, unavailableModalityForbidden, unavailableNoRegion,
            unavailableCopyMismatch,
        )
}

object RemoteTransferConsentModel {

    fun from(gate: RemoteTransferGate, copy: ConsentCopy): RemoteTransferConsentState = when (gate) {
        is RemoteTransferGate.Unavailable -> RemoteTransferConsentState.Unavailable(copy.messageFor(gate.reason))

        is RemoteTransferGate.Askable -> RemoteTransferConsentState.Askable(
            disclosureRows = rows(gate.disclosure, copy),
            grantLabel = copy.grant,
            declineLabel = copy.decline,
            prompt = copy.prompt,
        )
    }

    /**
     * Order is fixed and starts with where the data goes.
     *
     * The two facts the contract requires to be visible before the decision —
     * jurisdiction and processing region — are first, so a surface that truncates
     * or a reader that stops early still gets them.
     */
    private fun rows(disclosure: RemoteTransferDisclosure, copy: ConsentCopy): List<DisclosureRow> = listOf(
        DisclosureRow(DisclosureField.MARKET, copy.marketLabel, disclosure.marketJurisdiction),
        DisclosureRow(DisclosureField.PROCESSING_REGION, copy.processingRegionLabel, disclosure.processingRegion),
        DisclosureRow(
            DisclosureField.RETENTION,
            copy.retentionLabel,
            // v1 can only express zero, so the surface says "not kept" rather
            // than printing a number that will always be 0.
            if (disclosure.retentionDays == 0) copy.retentionNone else disclosure.retentionDays.toString(),
        ),
        DisclosureRow(DisclosureField.COPY_VERSION, copy.copyVersionLabel, disclosure.consentCopyVersion),
        DisclosureRow(
            DisclosureField.LOCALE,
            copy.localeLabel,
            "${disclosure.locale} · ${disclosure.measurementSystem}",
        ),
    )
}

private fun ConsentCopy.messageFor(reason: RemoteTransferUnavailable): String = when (reason) {
    RemoteTransferUnavailable.MARKET_UNRESOLVED -> unavailableMarketUnresolved
    RemoteTransferUnavailable.MARKET_PROHIBITED -> unavailableMarketProhibited
    RemoteTransferUnavailable.MODALITY_FORBIDDEN -> unavailableModalityForbidden
    RemoteTransferUnavailable.NO_PERMITTED_REGION -> unavailableNoRegion
    RemoteTransferUnavailable.CONSENT_COPY_MISMATCH -> unavailableCopyMismatch
}
