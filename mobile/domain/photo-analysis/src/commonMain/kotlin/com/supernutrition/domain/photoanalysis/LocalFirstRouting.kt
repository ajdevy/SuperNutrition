package com.supernutrition.domain.photoanalysis

import com.supernutrition.domain.inference.AttemptId
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.Confidence
import com.supernutrition.domain.inference.InferenceFailureCode
import com.supernutrition.domain.inference.InferenceOutcome
import com.supernutrition.domain.inference.PhotoInferenceResult
import com.supernutrition.domain.inference.isSha256Digest

/** Closed policies allowed before a signed G-5 threshold exists. */
enum class RoutePolicyMode {
    LOCAL_ONLY,
    MANUAL_CONFIRMATION,
    REMOTE_WITH_EXPLICIT_CONSENT,
}

data class GateG5LowConfidenceThreshold(val score: Double, val evidenceId: String) {
    init {
        require(score in 0.0..1.0) { "G-5 confidence threshold must be between zero and one" }
        require(evidenceId.isNotBlank()) { "G-5 evidence ID must not be blank" }
    }
}

fun interface GateG5EvidenceVerifier {
    fun isVerified(threshold: GateG5LowConfidenceThreshold, policy: RoutePolicySnapshot): Boolean
}

data class RoutePolicySnapshot(
    val policyVersion: String,
    val policyDigest: String,
    val mode: RoutePolicyMode,
    val gateG5: GateG5LowConfidenceThreshold? = null,
) {
    init {
        require(policyVersion.isNotBlank()) { "Route policy version must not be blank" }
        require(isSha256Digest(policyDigest)) { "Route policy digest must be SHA-256" }
    }
}

enum class RequestedRoute {
    DEFAULT,
    LOCAL,
    REMOTE,
}

enum class RemoteConsentState {
    NOT_REQUESTED,
    GRANTED,
    DECLINED,
}

data class RemoteConsentReceipt(
    val attemptId: AttemptId,
    val requestId: RequestId,
    val photoDigest: String,
    val purpose: String,
    val marketJurisdiction: String,
    val processingRegion: String,
    val policyVersion: String,
    val policyDigest: String,
    val configurationVersion: String,
    val configurationDigest: String,
    val consentCopyVersion: String,
    val receiptId: String,
    val signatureDigest: String,
) {
    init {
        require(isSha256Digest(photoDigest)) { "Consent photo digest must be SHA-256" }
        require(purpose.isNotBlank()) { "Consent purpose must not be blank" }
        require(marketJurisdiction.isNotBlank()) { "Consent market jurisdiction must not be blank" }
        require(processingRegion.isNotBlank()) { "Consent processing region must not be blank" }
        require(policyVersion.isNotBlank()) { "Consent policy version must not be blank" }
        require(isSha256Digest(policyDigest)) { "Consent policy digest must be SHA-256" }
        require(configurationVersion.isNotBlank()) { "Consent configuration version must not be blank" }
        require(isSha256Digest(configurationDigest)) { "Consent configuration digest must be SHA-256" }
        require(consentCopyVersion.isNotBlank()) { "Consent copy version must not be blank" }
        require(receiptId.isNotBlank()) { "Consent receipt ID must not be blank" }
        require(isSha256Digest(signatureDigest)) { "Consent signature must be SHA-256" }
    }
}

fun interface RemoteConsentVerifier {
    fun isValid(receipt: RemoteConsentReceipt, token: RouteAttemptToken, policy: RoutePolicySnapshot): Boolean
}

enum class RemoteEligibilityReason {
    EXPLICIT_USER_REQUEST,
    LOCAL_UNAVAILABLE,
    LOCAL_ABSTENTION,
    LOCAL_LOW_CONFIDENCE,
}

enum class LocalTerminalReason {
    SUCCESS,
    LOCAL_FAILURE,
    REMOTE_DISABLED,
    REMOTE_DECLINED,
}

data class RouteAttemptContext(
    val requestId: RequestId,
    val configurationVersion: String,
    val configurationDigest: String,
    val photoDigest: String,
) {
    init {
        require(configurationVersion.isNotBlank()) { "Route configuration version must not be blank" }
        require(isSha256Digest(configurationDigest)) { "Route configuration digest must be SHA-256" }
        require(isSha256Digest(photoDigest)) { "Route photo digest must be SHA-256" }
    }
}

data class RouteAttemptToken(
    val attemptId: AttemptId,
    val generation: Long,
    val context: RouteAttemptContext,
    val decisionRevision: Long = 0,
) {
    init {
        require(generation > 0) { "Route attempt generation must be positive" }
        require(decisionRevision >= 0) { "Route decision revision must not be negative" }
    }

    fun nextDecision(): RouteAttemptToken = copy(decisionRevision = decisionRevision + 1)
}

sealed interface RouteDecision {
    val token: RouteAttemptToken
    val decisionId: String

    data class RunLocal(override val token: RouteAttemptToken) : RouteDecision {
        override val decisionId: String = token.decisionId()
    }

    data class OfferRemote(
        override val token: RouteAttemptToken,
        val reason: RemoteEligibilityReason,
        val policyVersion: String,
        val policyDigest: String,
    ) : RouteDecision {
        override val decisionId: String = token.decisionId()
    }

    data class RunRemote(
        override val token: RouteAttemptToken,
        val reason: RemoteEligibilityReason,
        val consentReceipt: RemoteConsentReceipt,
    ) : RouteDecision {
        override val decisionId: String = token.decisionId()
    }

    data class KeepLocal(override val token: RouteAttemptToken, val reason: LocalTerminalReason) : RouteDecision {
        override val decisionId: String = token.decisionId()
    }

    data class Blocked(override val token: RouteAttemptToken, val reason: LocalTerminalReason) : RouteDecision {
        override val decisionId: String = token.decisionId()
    }
}

/** Pure local-first decision maker. It never transfers bytes or owns an Engine. */
class LocalFirstRouter(
    private val gateG5Verifier: GateG5EvidenceVerifier = GateG5EvidenceVerifier { _, _ -> false },
    private val consentVerifier: RemoteConsentVerifier = RemoteConsentVerifier { _, _, _ -> false },
) {
    @Suppress("LongParameterList")
    fun initial(
        token: RouteAttemptToken,
        localCapability: CapabilityState,
        policy: RoutePolicySnapshot,
        requestedRoute: RequestedRoute = RequestedRoute.DEFAULT,
        consent: RemoteConsentState = RemoteConsentState.NOT_REQUESTED,
        consentReceipt: RemoteConsentReceipt? = null,
    ): RouteDecision = when (requestedRoute) {
        RequestedRoute.REMOTE -> explicitRemote(token, policy, consent, consentReceipt)

        RequestedRoute.LOCAL -> if (localCapability == CapabilityState.READY) {
            RouteDecision.RunLocal(token.nextDecision())
        } else {
            RouteDecision.KeepLocal(token.nextDecision(), LocalTerminalReason.LOCAL_FAILURE)
        }

        RequestedRoute.DEFAULT -> if (localCapability == CapabilityState.READY) {
            RouteDecision.RunLocal(token.nextDecision())
        } else {
            afterLocalUnavailable(token, policy, consent, consentReceipt)
        }
    }

    fun afterLocal(
        token: RouteAttemptToken,
        result: PhotoInferenceResult,
        policy: RoutePolicySnapshot,
        consent: RemoteConsentState = RemoteConsentState.NOT_REQUESTED,
        consentReceipt: RemoteConsentReceipt? = null,
    ): RouteDecision {
        val trigger = result.remoteEligibilityReason(policy, gateG5Verifier)
            ?: return RouteDecision.KeepLocal(token.nextDecision(), result.terminalReason())
        return offerOrRunRemote(token, policy, consent, consentReceipt, trigger)
    }

    private fun explicitRemote(
        token: RouteAttemptToken,
        policy: RoutePolicySnapshot,
        consent: RemoteConsentState,
        consentReceipt: RemoteConsentReceipt?,
    ): RouteDecision = when {
        policy.mode == RoutePolicyMode.LOCAL_ONLY ->
            RouteDecision.Blocked(token.nextDecision(), LocalTerminalReason.REMOTE_DISABLED)

        consent == RemoteConsentState.GRANTED && isValidConsent(consentReceipt, token, policy) ->
            RouteDecision.RunRemote(
                token.nextDecision(),
                RemoteEligibilityReason.EXPLICIT_USER_REQUEST,
                requireNotNull(consentReceipt),
            )

        consent == RemoteConsentState.DECLINED -> RouteDecision.OfferRemote(
            token = token.nextDecision(),
            reason = RemoteEligibilityReason.EXPLICIT_USER_REQUEST,
            policyVersion = policy.policyVersion,
            policyDigest = policy.policyDigest,
        )

        else -> offerOrRunRemote(token, policy, consent, consentReceipt, RemoteEligibilityReason.EXPLICIT_USER_REQUEST)
    }

    private fun afterLocalUnavailable(
        token: RouteAttemptToken,
        policy: RoutePolicySnapshot,
        consent: RemoteConsentState,
        consentReceipt: RemoteConsentReceipt?,
    ): RouteDecision = offerOrRunRemote(
        token,
        policy,
        consent,
        consentReceipt,
        RemoteEligibilityReason.LOCAL_UNAVAILABLE,
    )

    private fun offerOrRunRemote(
        token: RouteAttemptToken,
        policy: RoutePolicySnapshot,
        consent: RemoteConsentState,
        consentReceipt: RemoteConsentReceipt?,
        reason: RemoteEligibilityReason,
    ): RouteDecision = when {
        policy.mode == RoutePolicyMode.LOCAL_ONLY ->
            RouteDecision.KeepLocal(token.nextDecision(), LocalTerminalReason.REMOTE_DISABLED)

        policy.mode == RoutePolicyMode.MANUAL_CONFIRMATION &&
            reason != RemoteEligibilityReason.EXPLICIT_USER_REQUEST ->
            RouteDecision.KeepLocal(token.nextDecision(), LocalTerminalReason.REMOTE_DISABLED)

        consent == RemoteConsentState.DECLINED ->
            RouteDecision.KeepLocal(token.nextDecision(), LocalTerminalReason.REMOTE_DECLINED)

        consent == RemoteConsentState.GRANTED && isValidConsent(consentReceipt, token, policy) ->
            RouteDecision.RunRemote(token.nextDecision(), reason, requireNotNull(consentReceipt))

        else -> RouteDecision.OfferRemote(
            token = token.nextDecision(),
            reason = reason,
            policyVersion = policy.policyVersion,
            policyDigest = policy.policyDigest,
        )
    }

    private fun isValidConsent(
        receipt: RemoteConsentReceipt?,
        token: RouteAttemptToken,
        policy: RoutePolicySnapshot,
    ): Boolean = receipt != null &&
        receipt.attemptId == token.attemptId &&
        receipt.requestId == token.context.requestId &&
        receipt.photoDigest == token.context.photoDigest &&
        receipt.policyVersion == policy.policyVersion &&
        receipt.policyDigest == policy.policyDigest &&
        receipt.configurationVersion == token.context.configurationVersion &&
        receipt.configurationDigest == token.context.configurationDigest &&
        consentVerifier.isValid(receipt, token, policy)
}

/** Attempt-owned token guard; replacement and cancellation invalidate old emissions. */
class RouteAttemptCoordinator(private val attemptId: AttemptId) {
    private var generation = 0L
    private var activeToken: RouteAttemptToken? = null
    private var activeEngine: RouteEngine? = null
    private var remoteOffered = false
    private var terminal = false

    fun start(context: RouteAttemptContext): RouteAttemptToken = RouteAttemptToken(
        attemptId = attemptId,
        generation = ++generation,
        context = context,
    ).also {
        activeToken = it
        activeEngine = null
        remoteOffered = false
        terminal = false
    }

    fun replace(context: RouteAttemptContext): RouteAttemptToken = start(context)

    fun cancel() {
        activeToken = null
        activeEngine = null
        remoteOffered = false
        terminal = true
    }

    fun accepts(decision: RouteDecision): Boolean {
        if (terminal) return false
        val token = decision.token
        val active = activeToken ?: return false
        if (!isCurrentDecision(active, token)) return false
        return when (decision) {
            is RouteDecision.RunLocal -> acceptLocal(token)

            is RouteDecision.OfferRemote -> acceptRemoteOffer(token)

            is RouteDecision.RunRemote -> acceptRemote(token)

            is RouteDecision.KeepLocal,
            is RouteDecision.Blocked,
            -> acceptTerminal()
        }
    }

    private fun isCurrentDecision(active: RouteAttemptToken, token: RouteAttemptToken): Boolean =
        active.attemptId == token.attemptId &&
            active.generation == token.generation &&
            token.context == active.context &&
            token.decisionRevision > active.decisionRevision

    private fun acceptLocal(token: RouteAttemptToken): Boolean {
        if (activeEngine != null) return false
        activeEngine = RouteEngine.LOCAL
        activeToken = token
        return true
    }

    private fun acceptRemoteOffer(token: RouteAttemptToken): Boolean {
        if (activeEngine == RouteEngine.REMOTE) return false
        remoteOffered = true
        activeToken = token
        return true
    }

    private fun acceptRemote(token: RouteAttemptToken): Boolean {
        if (activeEngine == RouteEngine.REMOTE) return false
        if (activeEngine == RouteEngine.LOCAL && !remoteOffered) return false
        activeEngine = RouteEngine.REMOTE
        activeToken = token
        return true
    }

    private fun acceptTerminal(): Boolean {
        terminal = true
        activeToken = null
        return true
    }
}

private enum class RouteEngine {
    LOCAL,
    REMOTE,
}

private fun RouteAttemptToken.decisionId(): String =
    "${attemptId.value}:${context.requestId.value}:$generation:$decisionRevision"

private fun PhotoInferenceResult.remoteEligibilityReason(
    policy: RoutePolicySnapshot,
    gateG5Verifier: GateG5EvidenceVerifier,
): RemoteEligibilityReason? = when (val outcome = outcome) {
    is InferenceOutcome.Failure -> if (outcome.code == InferenceFailureCode.MODEL_UNAVAILABLE) {
        RemoteEligibilityReason.LOCAL_UNAVAILABLE
    } else {
        null
    }

    is InferenceOutcome.Abstention -> RemoteEligibilityReason.LOCAL_ABSTENTION

    is InferenceOutcome.Candidates -> {
        val top = outcome.candidates.minBy { it.rank }
        if (policy.gateG5 != null &&
            gateG5Verifier.isVerified(policy.gateG5, policy) &&
            top.confidence.isBelow(policy.gateG5)
        ) {
            RemoteEligibilityReason.LOCAL_LOW_CONFIDENCE
        } else {
            null
        }
    }
}

private fun PhotoInferenceResult.terminalReason(): LocalTerminalReason = when (outcome) {
    is InferenceOutcome.Failure -> LocalTerminalReason.LOCAL_FAILURE
    else -> LocalTerminalReason.SUCCESS
}

private fun Confidence.isBelow(threshold: GateG5LowConfidenceThreshold): Boolean = score < threshold.score
