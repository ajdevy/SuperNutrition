package com.supernutrition.feature.photoanalysis

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.supernutrition.core.config.MarketId
import com.supernutrition.core.config.MarketPolicy
import com.supernutrition.core.config.MarketPolicyRegistry
import com.supernutrition.core.config.RemotePermission
import com.supernutrition.core.config.ResidencyPosture
import com.supernutrition.domain.inference.CalibratedConfidence
import com.supernutrition.domain.inference.CapabilityReason
import com.supernutrition.domain.inference.CapabilityState
import com.supernutrition.domain.inference.ComponentAmbiguity
import com.supernutrition.domain.inference.ComponentVisibility
import com.supernutrition.domain.inference.ConfidenceBand
import com.supernutrition.domain.inference.GramRange
import com.supernutrition.domain.inference.INFERENCE_EVIDENCE_CONTRACT_V2
import com.supernutrition.domain.inference.InferenceRoute
import com.supernutrition.domain.inference.InferenceV2Capability
import com.supernutrition.domain.inference.InferenceV2Engine
import com.supernutrition.domain.inference.InferenceV2Outcome
import com.supernutrition.domain.inference.InferenceV2Provenance
import com.supernutrition.domain.inference.InferenceV2Request
import com.supernutrition.domain.inference.InferenceV2Result
import com.supernutrition.domain.inference.LocalizedCandidateIdentity
import com.supernutrition.domain.inference.MassCalibrationAssumption
import com.supernutrition.domain.inference.MassEvidence
import com.supernutrition.domain.inference.NormalizedVisibleRegion
import com.supernutrition.domain.inference.VisibleComponent
import com.supernutrition.domain.photoanalysis.AttemptScopedConsentStore
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
import com.supernutrition.domain.photoanalysis.RemoteTransferConsentPolicy
import com.supernutrition.domain.photoanalysis.RemoteTransferContext
import com.supernutrition.domain.photoanalysis.RemoteTransferDisclosure
import com.supernutrition.domain.photoanalysis.RemoteTransferGate
import com.supernutrition.domain.photoanalysis.TransferConsentDecision
import com.supernutrition.domain.photoanalysis.TransferConsentReceipt
import com.supernutrition.inference.remote.GatewayEnvironment
import com.supernutrition.inference.remote.HttpGatewayTransport
import com.supernutrition.inference.remote.RemoteConsentDecision
import com.supernutrition.inference.remote.RemoteInferenceEngine
import kotlinx.coroutines.CoroutineScope
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Binds the analysis engines for the Android host.
 *
 * Two slots, per the E4 seam: the v1 dish slot is the first-party gateway
 * engine, the v2 component slot stays local. Both are constructed here because
 * this module is the Android composition root — the same place
 * `AndroidPhotoBridge` and `AndroidImageNormalizer` are bound.
 *
 * EVERYTHING HERE IS FENCED TO TEST MODE. [isTestMode] must be true or
 * [testModeLauncher] returns [PhotoAnalysisAnalysisLauncher.None] and the host
 * behaves exactly as it did before this file existed. The fence exists because
 * two of the inputs below are not production behaviour and must never become a
 * silent default (AD-40):
 *
 *  - consent is synthesised rather than collected from the user. Production
 *    consent UX is a separate slice; a synthesised receipt reaching a real
 *    market would be exactly the silent remote upload AD-40 forbids.
 *  - the local component runtime is a fixture, not a model.
 *
 * The gateway itself is unchanged: every gate, the envelope, the consent digest
 * binding and the disposal contract are the real ones.
 */
internal object PhotoAnalysisEngineWiring {

    /** Manifest key carrying the gateway base URL, so the host is configurable. */
    private const val BASE_URL_KEY = "com.supernutrition.gatewayBaseUrl"
    private const val TEST_MODE_KEY = "com.supernutrition.testMode"

    /** 10.0.2.2 is the loopback alias an Android emulator uses for its host. */
    const val EMULATOR_HOST_GATEWAY: String = "http://10.0.2.2:3000"

    private const val FIXTURE_MARKET = "ZZ"

    fun isTestMode(context: Context): Boolean {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return debuggable && metadata(context).getBoolean(TEST_MODE_KEY, false)
    }

    fun baseUrl(context: Context): String = metadata(context).getString(BASE_URL_KEY) ?: EMULATOR_HOST_GATEWAY

    private fun metadata(context: Context) = runCatching {
        context.packageManager
            .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData
    }.getOrNull() ?: android.os.Bundle.EMPTY

    /**
     * Returns a launcher bound to both engines, or [PhotoAnalysisAnalysisLauncher.None]
     * outside test mode. The caller does not decide the fence.
     */
    @Suppress("LongParameterList")
    fun testModeLauncher(
        context: Context,
        feature: PhotoAnalysisFeature,
        sessions: PhotoSessionStorePort,
        consentStore: AttemptScopedConsentStore,
        scope: CoroutineScope,
        configuration: PhotoAnalysisAnalysisConfiguration,
    ): PhotoAnalysisAnalysisLauncher {
        if (!isTestMode(context)) return PhotoAnalysisAnalysisLauncher.None

        val currentInput = { readCurrentInput(feature, sessions) }
        return CoordinatedPhotoAnalysisAnalysisLauncher(
            feature = feature,
            dishEngine = RemoteInferenceEngine(
                transport = HttpGatewayTransport(baseUrl(context)),
                environment = GatewayEnvironment(
                    baseUrl = baseUrl(context),
                    claimedMarketJurisdiction = FIXTURE_MARKET,
                    catalogCountry = FIXTURE_MARKET,
                    locale = "en-GB",
                    measurementSystem = "metric",
                    configurationVersion = configuration.configurationVersion,
                    authorization = "Bearer test.installation-0001",
                ),
                // The bytes actually transferred, so the digest the gateway
                // recomputes is bound to them rather than to a second read.
                photoBytes = { currentInput()?.bytes },
                digest = ::sha256Digest,
                consent = { attemptId -> recordedConsent(consentStore, attemptId, currentInput()) },
                idFactory = ::opaqueToken,
            ),
            componentEngine = FixtureComponentEngine,
            scope = scope,
            configuration = configuration,
        )
    }

    private fun readCurrentInput(
        feature: PhotoAnalysisFeature,
        sessions: PhotoSessionStorePort,
    ): NormalizedPhotoInput? = when (val state = feature.state) {
        is PhotoAnalysisState.Ready -> sessions.read(state.input.ref)
        is PhotoAnalysisState.Analyzing -> sessions.read(state.ref)
        else -> null
    }

    /**
     * The decision the user actually made, or a refusal.
     *
     * Slice A synthesised a granted receipt here. Nothing synthesises one now:
     * the store is empty until [RemoteTransferConsentScreen] reports a press, and
     * an empty store yields `granted = false`, which [RemoteInferenceEngine]
     * turns into a typed failure without opening a socket.
     *
     * The lookup is keyed by attempt AND by the digest of the bytes about to be
     * sent, so a decision cannot survive the payload changing under it — the
     * binding `contracts/mobile-api/v1/consent-receipt.toml` requires
     * (`digest_covers = "scrubbed-normalized-payload-bytes"`).
     */
    private fun recordedConsent(
        store: AttemptScopedConsentStore,
        attemptId: String,
        input: NormalizedPhotoInput?,
    ): RemoteConsentDecision {
        val receipt = input?.let { store.receiptFor(attemptId, sha256Digest(it.bytes)) }
            ?: return refused()
        return RemoteConsentDecision(
            granted = receipt.decision == TransferConsentDecision.GRANTED,
            consentCopyVersion = receipt.consentCopyVersion,
            processingRegion = receipt.processingRegion,
            marketJurisdiction = receipt.marketJurisdiction,
            retentionVersion = receipt.retentionVersion,
            recordedAt = receipt.recordedAt,
            expiresAt = receipt.expiresAt,
        )
    }

    /** No receipt is a refusal, never an absence the caller might read past. */
    private fun refused() = RemoteConsentDecision(
        granted = false,
        consentCopyVersion = null,
        processingRegion = null,
        marketJurisdiction = FIXTURE_MARKET,
        retentionVersion = "retention/2",
        recordedAt = iso(Instant.now().truncatedTo(ChronoUnit.SECONDS)),
        expiresAt = iso(Instant.now().truncatedTo(ChronoUnit.SECONDS)),
    )

    private fun iso(instant: Instant) = instant.toString().replace(".000Z", "Z")

    private const val HEX = "0123456789abcdef"

    /**
     * Hex from a literal alphabet rather than String.format: the consent digest
     * must be byte-identical to the gateway's, and format() renders digits per
     * the default locale.
     */
    fun sha256Digest(bytes: ByteArray): String = buildString {
        append("sha256:")
        for (byte in MessageDigest.getInstance("SHA-256").digest(bytes)) {
            val value = byte.toInt() and 0xFF
            append(HEX[value ushr 4])
            append(HEX[value and 0x0F])
        }
    }

    /** 20 alphanumerics; the engine adds the `eff_`/`req_` prefixes itself. */
    private fun opaqueToken(): String {
        val bytes = ByteArray(10).also { java.security.SecureRandom().nextBytes(it) }
        return buildString {
            for (byte in bytes) {
                val value = byte.toInt() and 0xFF
                append(HEX[value ushr 4])
                append(HEX[value and 0x0F])
            }
        }
    }
}

/**
 * The consent half of the wiring: the gate this build can offer, and the receipt
 * a decision produces. Separate from [PhotoAnalysisEngineWiring] because they are
 * a coherent group with their own reason to change — the market policy — and
 * because keeping them there pushed that object past the function limit.
 */
internal object PhotoAnalysisConsentWiring {

    private const val FIXTURE_MARKET = "ZZ"
    private const val RETENTION_VERSION = "retention/2"
    private const val CONSENT_TTL_MINUTES = 10L

    /**
     * The disclosure this build can render.
     *
     * These are the gateway's own TEST_MODE fixture values
     * (`apps/api/src/security/market-policy.resolver.ts` FIXTURE_MARKET_POLICY),
     * NOT the ones in `contracts/market-policy/v1/fixtures/permitted-market.toml`,
     * which say `photo-transfer-copy-v1` / `fixture-region-1`. The two fixtures
     * disagree; the gateway fail-closes on
     * `consent-copy-version-known-for-modality` and
     * `processing-region-in-permitted-list`, so a client presenting the contract's
     * values is rejected by the running server. Matching the server is what makes
     * an end-to-end run possible; reconciling the two fixtures is a
     * contract-owner call recorded in the Slice C report.
     */
    private const val PRESENTED_CONSENT_COPY_VERSION = "photo-consent/fixture-1"
    private const val FIXTURE_PROCESSING_REGION = "fixture-region"

    /**
     * The market this test build knows about.
     *
     * `MarketPolicyRegistry.unresolved` is empty and stays empty — declaring a
     * market is a legal decision, not a code change. This registry exists only
     * behind the same TEST_MODE fence as the rest of the wiring, so a release
     * build still resolves nothing and still offers no transfer.
     */
    private val testMarketRegistry = MarketPolicyRegistry(
        listOf(
            MarketPolicy(
                marketId = MarketId(FIXTURE_MARKET),
                jurisdiction = "fixture-jurisdiction",
                residencyPosture = ResidencyPosture.PERMITTED_WITH_REGION,
                permittedProcessingRegions = listOf(FIXTURE_PROCESSING_REGION),
                photoRemotePermission = RemotePermission.PERMITTED,
                textRemotePermission = RemotePermission.FORBIDDEN,
                photoConsentCopyVersion = PRESENTED_CONSENT_COPY_VERSION,
                textConsentCopyVersion = null,
                acceptedInputLanguages = listOf("en", "ru"),
                legalReviewApprover = "fixture-approver",
                // Far enough out that the fixture does not expire mid-test; the
                // expiry path itself is covered by RemoteTransferConsentTest.
                legalReviewExpiresOnEpochDay = 30_000,
            ),
        ),
    )

    /**
     * Builds the receipt for a decision the user just made, bound to the bytes
     * that will actually be sent.
     */
    fun receiptFor(
        decision: TransferConsentDecision,
        input: NormalizedPhotoInput,
        disclosure: RemoteTransferDisclosure?,
    ): TransferConsentReceipt {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        return TransferConsentReceipt(
            version = TransferConsentReceipt.RECEIPT_VERSION,
            // The contract shape, derived from the bytes so it is stable for this
            // attempt without leaking the host's own attempt id.
            attemptId = "att_" + sha256(input.bytes).removePrefix("sha256:").take(32),
            digest = sha256(input.bytes),
            modality = TransferConsentReceipt.PHOTO_MODALITY,
            purpose = TransferConsentReceipt.FOOD_IDENTIFICATION,
            decision = decision,
            marketJurisdiction = disclosure?.marketJurisdiction ?: FIXTURE_MARKET,
            processingRegion = disclosure?.processingRegion,
            consentCopyVersion = disclosure?.consentCopyVersion,
            retentionVersion = disclosure?.retentionVersion ?: RETENTION_VERSION,
            retentionDays = 0,
            configurationVersion = disclosure?.configurationVersion ?: "cfg/1",
            recordedAt = iso(now),
            expiresAt = iso(now.plus(CONSENT_TTL_MINUTES, ChronoUnit.MINUTES)),
        )
    }

    /** The gate for this build. Test-fenced by its only caller. */
    fun gate(configurationVersion: String, todayEpochDay: Long): RemoteTransferGate =
        RemoteTransferConsentPolicy(testMarketRegistry, RETENTION_VERSION).gate(
            marketId = FIXTURE_MARKET,
            presentedConsentCopyVersion = PRESENTED_CONSENT_COPY_VERSION,
            context = RemoteTransferContext(configurationVersion, "en-GB", "metric"),
            todayEpochDay = todayEpochDay,
        )

    private fun iso(instant: Instant) = instant.toString().replace(".000Z", "Z")

    private fun sha256(bytes: ByteArray) = PhotoAnalysisEngineWiring.sha256Digest(bytes)
}

/**
 * A component engine that reports one fixed visible component.
 *
 * WHY THIS IS NOT LocalAndroidComponentInferenceEngine WITH A FIXTURE RUNTIME.
 * That engine calls `enforceProductionActivation()`, which refuses any manifest
 * whose activation is not PRODUCTION — a correct gate that stops an unapproved
 * model from serving. Satisfying it would mean stamping a fixture with
 * production, engineering, legal, corpus, benchmark and rollback approval refs
 * it does not have. Forging those to make a demo run would disarm exactly the
 * control that matters, so the fenced path declares itself a fixture instead
 * and that gate is left untouched.
 *
 * The coordinator runs the component engine before the dish engine and abandons
 * the attempt when it yields nothing, so without this the gateway is never
 * reached on a device that has no local model.
 *
 * Slice B replaces this with the real local component model.
 */
private object FixtureComponentEngine : InferenceV2Engine() {

    private const val ENGINE_ID = "fixture-component-engine"
    private const val MODEL_VERSION = "fixture-component-model-1"
    private const val TAXONOMY_VERSION = "ingredient-taxonomy-1"
    private const val CALIBRATION_VERSION = "mass-calibration-1"

    override val contractVersion: String = INFERENCE_EVIDENCE_CONTRACT_V2

    override suspend fun capability(): InferenceV2Capability = InferenceV2Capability(
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        state = CapabilityState.READY,
        reason = CapabilityReason.NONE,
        userMessageKey = "inference.capability.fixture-ready",
        engineId = ENGINE_ID,
        modelVersion = MODEL_VERSION,
        taxonomyVersion = TAXONOMY_VERSION,
        calibrationVersion = CALIBRATION_VERSION,
    )

    override suspend fun analyzeUnvalidated(request: InferenceV2Request): InferenceV2Result = InferenceV2Result(
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
        inputModality = request.input.modality,
        outcome = InferenceV2Outcome.Components(listOf(COMPONENT)),
    )

    private val PROVENANCE = InferenceV2Provenance(
        route = InferenceRoute.LOCAL,
        engineId = ENGINE_ID,
        modelVersion = MODEL_VERSION,
        taxonomyVersion = TAXONOMY_VERSION,
        calibrationVersion = CALIBRATION_VERSION,
        contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
    )

    private val COMPONENT = VisibleComponent(
        componentId = "component:fixture",
        candidate = LocalizedCandidateIdentity("ingredient:leafy-greens", "Leafy greens", "en"),
        confidence = CalibratedConfidence(0.9, ConfidenceBand.HIGH),
        visibility = ComponentVisibility.VISIBLE,
        ambiguity = ComponentAmbiguity.NONE,
        region = NormalizedVisibleRegion(0.1, 0.1, 0.4, 0.4),
        mass = MassEvidence.Estimated(
            range = GramRange(100.0, 140.0),
            calibrationAssumptions = listOf(MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION),
        ),
        provenance = PROVENANCE,
    )
}
