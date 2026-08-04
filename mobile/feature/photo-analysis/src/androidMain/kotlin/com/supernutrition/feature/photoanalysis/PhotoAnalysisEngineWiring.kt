package com.supernutrition.feature.photoanalysis

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import com.supernutrition.domain.photoanalysis.NormalizedPhotoInput
import com.supernutrition.domain.photoanalysis.PhotoAnalysisState
import com.supernutrition.domain.photoanalysis.PhotoSessionStorePort
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
    private const val CONSENT_TTL_MINUTES = 10L

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
    fun testModeLauncher(
        context: Context,
        feature: PhotoAnalysisFeature,
        sessions: PhotoSessionStorePort,
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
                digest = ::sha256,
                consent = { testModeConsent() },
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
     * A synthesised, always-granted decision for the fixture market only.
     *
     * This is the piece that must never escape test mode: it asserts a consent
     * the user was never asked for. It names the fixture market so that even if
     * the fence were bypassed the gateway's own market policy rejects it.
     */
    private fun testModeConsent(): RemoteConsentDecision {
        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
        return RemoteConsentDecision(
            granted = true,
            consentCopyVersion = "photo-consent/fixture-1",
            processingRegion = "fixture-region",
            marketJurisdiction = FIXTURE_MARKET,
            retentionVersion = "retention/2",
            recordedAt = iso(now),
            expiresAt = iso(now.plus(CONSENT_TTL_MINUTES, ChronoUnit.MINUTES)),
        ).also { require(it.marketJurisdiction == FIXTURE_MARKET) { "test-mode consent is fixture-market only" } }
    }

    private fun iso(instant: Instant) = instant.toString().replace(".000Z", "Z")

    private const val HEX = "0123456789abcdef"

    /**
     * Hex from a literal alphabet rather than String.format: the consent digest
     * must be byte-identical to the gateway's, and format() renders digits per
     * the default locale.
     */
    private fun sha256(bytes: ByteArray): String = buildString {
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
