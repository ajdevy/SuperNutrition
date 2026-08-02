package com.supernutrition.domain.inference

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs

@Suppress("LargeClass")
class InferenceV2ContractTest {
    @Test
    fun `v2 engine uses an opaque attempt-bound photo input`() {
        val attempt = AttemptId("attempt-v2-1")
        val request = InferenceV2Request(
            input = InferenceV2Input.Photo(PhotoRef.issue(attempt, "leased-v2-photo-1")),
            attemptContext = InferenceAttemptContext(
                attemptId = attempt,
                requestId = "request-v2-1",
                configurationVersion = "config-v2-1",
                configurationDigest = "a".repeat(64),
            ),
        )
        val engine = object : InferenceV2Engine() {
            override val contractVersion: String = INFERENCE_EVIDENCE_CONTRACT_V2

            override suspend fun capability(): InferenceV2Capability = InferenceV2ContractFixtures.readyCapability

            override suspend fun analyzeUnvalidated(request: InferenceV2Request): InferenceV2Result =
                InferenceV2ContractFixtures.estimatedPhotoResult
        }

        assertEquals(attempt, (request.input as InferenceV2Input.Photo).photoRef.attemptId)
        assertEquals(INFERENCE_EVIDENCE_CONTRACT_V2, engine.contractVersion)
        assertFailsWith<IllegalArgumentException> {
            InferenceV2Request(
                input = request.input,
                attemptContext = request.attemptContext.copy(attemptId = AttemptId("other-v2-attempt")),
            )
        }
    }

    @Test
    fun `v2 input resolution rejects missing ambiguous and invalid text modality`() {
        val attempt = AttemptId("attempt-v2-input")
        val photo = PhotoRef.issue(attempt, "leased-v2-input")

        assertIs<InferenceV2InputResolution.Invalid>(
            InferenceV2Input.resolve(photoRef = null, text = null, inputLocale = null),
        ).also { assertEquals(InferenceV2InputError.MISSING_INPUT, it.error) }
        assertIs<InferenceV2InputResolution.Invalid>(
            InferenceV2Input.resolve(photoRef = photo, text = "beans", inputLocale = "en"),
        ).also { assertEquals(InferenceV2InputError.AMBIGUOUS_INPUT, it.error) }
        assertIs<InferenceV2InputResolution.Invalid>(
            InferenceV2Input.resolve(photoRef = null, text = "beans", inputLocale = " "),
        ).also { assertEquals(InferenceV2InputError.TEXT_LOCALE_REQUIRED, it.error) }
        assertIs<InferenceV2InputResolution.Valid>(
            InferenceV2Input.resolve(photoRef = photo, text = null, inputLocale = null),
        )
        assertIs<InferenceV2InputResolution.Valid>(
            InferenceV2Input.resolve(photoRef = null, text = "beans", inputLocale = "en"),
        )
        assertIs<InferenceV2InputResolution.Invalid>(
            InferenceV2Input.resolve(photoRef = photo, text = null, inputLocale = "en"),
        ).also { assertEquals(InferenceV2InputError.PHOTO_LOCALE_FORBIDDEN, it.error) }
        assertIs<InferenceV2InputResolution.Invalid>(
            InferenceV2Input.resolve(photoRef = null, text = "beans", inputLocale = "invalid_locale"),
        ).also { assertEquals(InferenceV2InputError.TEXT_LOCALE_INVALID, it.error) }
    }

    @Test
    fun `estimated grams are a bounded range and every component has one mass state`() {
        val component = (InferenceV2ContractFixtures.estimatedPhotoResult.outcome as InferenceV2Outcome.Components)
            .components
            .single()
        assertIs<MassEvidence.Estimated>(component.mass)

        assertFailsWith<IllegalArgumentException> { GramRange(-1.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { GramRange(2.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { GramRange(1.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { GramRange(Double.NaN, 1.0) }
        assertFailsWith<IllegalArgumentException> {
            MassEvidence.Estimated(
                range = GramRange(1.0, 2.0),
                calibrationAssumptions = emptyList(),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2Outcome.Components(listOf(component, component.copy()))
        }
        assertFailsWith<IllegalArgumentException> {
            NormalizedVisibleRegion(left = 0.9, top = 0.1, width = 0.2, height = 0.2)
        }

        val callerComponents = mutableListOf(component)
        val stableComponents = InferenceV2Outcome.Components(callerComponents)
        callerComponents.clear()
        assertEquals(1, stableComponents.components.size)
        val callerAssumptions = mutableListOf(MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION)
        val stableMass = MassEvidence.Estimated(GramRange(1.0, 2.0), callerAssumptions)
        callerAssumptions.clear()
        assertEquals(1, stableMass.calibrationAssumptions.size)
    }

    @Test
    fun `text output cannot carry an estimated mass`() {
        val estimatedComponent =
            (InferenceV2ContractFixtures.estimatedPhotoResult.outcome as InferenceV2Outcome.Components)
                .components
                .single()
        assertFailsWith<IllegalArgumentException> {
            InferenceV2Result(
                contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
                inputModality = InferenceInputModality.TEXT,
                outcome = InferenceV2Outcome.Components(listOf(estimatedComponent)),
            )
        }
        assertEquals(
            InferenceInputModality.TEXT,
            InferenceV2ContractFixtures.textNotEvaluableResult.inputModality,
        )
    }

    @Test
    fun `v2 json is canonical ignores unknowns and rejects invalid evidence`() {
        val encoded = InferenceV2ContractJson.encode(InferenceV2ContractFixtures.estimatedPhotoResult)
        val expected = """
            {"contractVersion":"inference-evidence/v2","inputModality":"photo","outcome":{"outcomeType":"components",
            "components":[{"componentId":"component:salmon-1","candidate":{"candidateId":"ingredient:salmon",
            "label":"Salmon","locale":"en"},"confidence":{"score":0.91,"band":"high"},
            "visibility":"visible","ambiguity":"none","region":{"left":0.1,"top":0.2,"width":0.3,"height":0.4},
            "mass":{"massType":"estimated","range":{"lowerGrams":120.0,"upperGrams":155.0},
            "calibrationAssumptions":["single-view-approximation"]},"provenance":{"route":"local",
            "engineId":"fixture-component-engine","modelVersion":"fixture-component-model-1",
            "taxonomyVersion":"fixture-ingredient-taxonomy-1","calibrationVersion":"fixture-mass-calibration-1",
            "contractVersion":"inference-evidence/v2"}}]}}
        """.trimIndent().replace("\n", "")

        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(encoded))
        assertEquals(
            InferenceV2ContractFixtures.estimatedPhotoResult,
            InferenceV2ContractJson.decode(
                encoded.replaceFirst("{\"contractVersion\"", "{\"futureEnvelopeField\":true,\"contractVersion\""),
            ),
        )
        assertFailsWith<SerializationException> {
            InferenceV2ContractJson.decode(encoded.replace("\"mass\":", "\"removedMass\":"))
        }
        assertFailsWith<SerializationException> {
            InferenceV2ContractJson.decode(encoded.replace("\"high\"", "\"unknown\""))
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2ContractJson.decode(encoded.replace("\"upperGrams\":155.0", "\"upperGrams\":-1.0"))
        }
    }

    @Test
    fun `text output cannot carry visual region evidence`() {
        val component = (InferenceV2ContractFixtures.textNotEvaluableResult.outcome as InferenceV2Outcome.Components)
            .components
            .single()
        assertEquals(null, component.region)
        assertFailsWith<IllegalArgumentException> {
            InferenceV2Result(
                contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
                inputModality = InferenceInputModality.TEXT,
                outcome = InferenceV2Outcome.Components(
                    listOf(component.copy(region = NormalizedVisibleRegion(0.1, 0.2, 0.3, 0.4))),
                ),
            )
        }
    }

    @Test
    fun `v2 canonical fixtures cover every outcome capability and telemetry shape`() {
        assertCanonicalOutcomeFixtures()
        assertCanonicalCapabilityFixture()
        assertCanonicalTelemetryFixture()
    }

    private fun assertCanonicalOutcomeFixtures() {
        canonicalOutcomeFixtures().forEach { (result, expected) ->
            assertEquals(
                Json.parseToJsonElement(expected),
                Json.parseToJsonElement(InferenceV2ContractJson.encode(result)),
            )
            assertEquals(result, InferenceV2ContractJson.decode(InferenceV2ContractJson.encode(result)))
        }
    }

    private fun canonicalOutcomeFixtures(): Map<InferenceV2Result, String> = mapOf(
        InferenceV2ContractFixtures.notEvaluablePhotoResult to """
            {"contractVersion":"inference-evidence/v2","inputModality":"photo","outcome":{"outcomeType":"components",
            "components":[{"componentId":"component:vegetables-1","candidate":{"candidateId":"ingredient:mixed-vegetables","label":"Mixed vegetables","locale":"en"},
            "confidence":{"score":0.91,"band":"high"},"visibility":"visible","ambiguity":"none","region":{"left":0.1,"top":0.2,"width":0.3,"height":0.4},
            "mass":{"massType":"not-evaluable","reason":"mixed-or-occluded","userMessageKey":"inference.mass.mixed_or_occluded"},
            "provenance":{"route":"local","engineId":"fixture-component-engine","modelVersion":"fixture-component-model-1",
            "taxonomyVersion":"fixture-ingredient-taxonomy-1","calibrationVersion":"fixture-mass-calibration-1","contractVersion":"inference-evidence/v2"}}]}}
        """.trimIndent().replace("\n", ""),
        InferenceV2ContractFixtures.textNotEvaluableResult to """
            {"contractVersion":"inference-evidence/v2","inputModality":"text","outcome":{"outcomeType":"components",
            "components":[{"componentId":"component:beans-1","candidate":{"candidateId":"ingredient:black-beans","label":"Black beans","locale":"en"},
            "confidence":{"score":0.91,"band":"high"},"visibility":"visible","ambiguity":"none",
            "mass":{"massType":"not-evaluable","reason":"text-has-no-visible-portion","userMessageKey":"inference.mass.text_has_no_visible_portion"},
            "provenance":{"route":"local","engineId":"fixture-component-engine","modelVersion":"fixture-component-model-1",
            "taxonomyVersion":"fixture-ingredient-taxonomy-1","calibrationVersion":"fixture-mass-calibration-1","contractVersion":"inference-evidence/v2"}}]}}
        """.trimIndent().replace("\n", ""),
        InferenceV2ContractFixtures.abstentionResult to """
            {"contractVersion":"inference-evidence/v2","inputModality":"photo","outcome":{"outcomeType":"abstention","reason":"no-food-detected",
            "userMessageKey":"inference.abstention.no_food_detected","provenance":{"route":"local","engineId":"fixture-component-engine",
            "modelVersion":"fixture-component-model-1","taxonomyVersion":"fixture-ingredient-taxonomy-1",
            "calibrationVersion":"fixture-mass-calibration-1","contractVersion":"inference-evidence/v2"}}}
        """.trimIndent().replace("\n", ""),
        InferenceV2ContractFixtures.failureResult to """
            {"contractVersion":"inference-evidence/v2","inputModality":"photo","outcome":{"outcomeType":"failure","code":"temporary-unavailable","retryable":true,
            "userMessageKey":"inference.failure.temporary_unavailable","provenance":{"route":"local","engineId":"fixture-component-engine",
            "modelVersion":"fixture-component-model-1","taxonomyVersion":"fixture-ingredient-taxonomy-1",
            "calibrationVersion":"fixture-mass-calibration-1","contractVersion":"inference-evidence/v2"}}}
        """.trimIndent().replace("\n", ""),
    )

    private fun assertCanonicalCapabilityFixture() {
        val expectedCapability = """
            {"contractVersion":"inference-evidence/v2","state":"ready","reason":"none","userMessageKey":"inference.capability.ready",
            "engineId":"fixture-component-engine","modelVersion":"fixture-component-model-1","taxonomyVersion":"fixture-ingredient-taxonomy-1",
            "calibrationVersion":"fixture-mass-calibration-1"}
        """.trimIndent().replace("\n", "")
        assertEquals(
            Json.parseToJsonElement(expectedCapability),
            Json.parseToJsonElement(
                InferenceV2ContractJson.encodeCapability(InferenceV2ContractFixtures.readyCapability),
            ),
        )
        assertEquals(
            InferenceV2ContractFixtures.readyCapability,
            InferenceV2ContractJson.decodeCapability(
                InferenceV2ContractJson.encodeCapability(InferenceV2ContractFixtures.readyCapability),
            ),
        )
    }

    private fun assertCanonicalTelemetryFixture() {
        val expectedTelemetry = """
            {"contractVersion":"inference-evidence/v2","eventType":"attempt","route":"local","capabilityState":"ready",
            "outcome":"components","durationBucket":"1-to-5s","resourceBucket":"within-budget","componentCountBucket":"one"}
        """.trimIndent().replace("\n", "")
        assertEquals(
            Json.parseToJsonElement(expectedTelemetry),
            Json.parseToJsonElement(
                InferenceV2ContractJson.encodeTelemetry(InferenceV2ContractFixtures.telemetryEvent),
            ),
        )
        assertEquals(
            InferenceV2ContractFixtures.telemetryEvent,
            InferenceV2ContractJson.decodeTelemetry(
                InferenceV2ContractJson.encodeTelemetry(InferenceV2ContractFixtures.telemetryEvent),
            ),
        )
    }

    @Test
    fun `v2 capability provenance and user-safe reasons reject malformed values`() {
        val component = (InferenceV2ContractFixtures.estimatedPhotoResult.outcome as InferenceV2Outcome.Components)
            .components
            .single()
        assertFailsWith<IllegalArgumentException> {
            InferenceV2ContractFixtures.readyCapability.copy(contractVersion = PHOTO_INFERENCE_CONTRACT_V1)
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2ContractFixtures.readyCapability.copy(reason = CapabilityReason.MODEL_MISSING)
        }
        assertFailsWith<IllegalArgumentException> { component.copy(componentId = "not a bounded id") }
        assertFailsWith<IllegalArgumentException> { component.candidate.copy(locale = "invalid_locale") }
        assertFailsWith<IllegalArgumentException> {
            MassEvidence.NotEvaluable(MassNotEvaluableReason.AMBIGUOUS_PORTION, " ")
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2Provenance(
                route = InferenceRoute.LOCAL,
                engineId = " ",
                modelVersion = "model",
                taxonomyVersion = "taxonomy",
                calibrationVersion = "calibration",
                contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
            )
        }
    }

    @Suppress("LongMethod")
    @Test
    fun `v2 remaining invariants fail closed and all typed evidence states serialize`() {
        val estimatedComponent =
            (InferenceV2ContractFixtures.estimatedPhotoResult.outcome as InferenceV2Outcome.Components)
                .components
                .single()
        val provenance = estimatedComponent.provenance
        val attempt = AttemptId("attempt-v2-text")
        val text = InferenceV2Input.Text("beans", "en")

        assertEquals("InferenceV2Input.Text(redacted)", text.toString())
        assertEquals(text, InferenceV2Input.Text("beans", "en"))
        assertFalse(text == InferenceV2Input.Text("rice", "en"))
        assertEquals(
            "InferenceV2Input.Photo(redacted)",
            InferenceV2Input.Photo(PhotoRef.issue(attempt, "leased-v2-redacted")).toString(),
        )
        assertFailsWith<IllegalArgumentException> { InferenceV2Input.Text(" ", "en") }
        assertFailsWith<IllegalArgumentException> { InferenceV2Input.Text("x".repeat(2_001), "en") }
        assertFailsWith<IllegalArgumentException> { InferenceV2Input.Text("beans", "invalid_locale") }
        assertEquals(
            InferenceInputModality.TEXT,
            InferenceV2Request(
                input = text,
                attemptContext = InferenceAttemptContext(
                    attemptId = attempt,
                    requestId = "request-v2-text",
                    configurationVersion = "config-v2-text",
                    configurationDigest = "b".repeat(64),
                ),
            ).input.modality,
        )

        val unavailable = InferenceV2Capability(
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
            state = CapabilityState.MODEL_REQUIRED,
            reason = CapabilityReason.MODEL_MISSING,
            userMessageKey = "inference.capability.model_required",
            engineId = "fixture-component-engine",
            modelVersion = "unavailable",
            taxonomyVersion = "unavailable",
            calibrationVersion = "unavailable",
        )
        assertEquals(CapabilityState.MODEL_REQUIRED, unavailable.state)
        assertFailsWith<IllegalArgumentException> { unavailable.copy(reason = CapabilityReason.NONE) }
        assertFailsWith<IllegalArgumentException> { unavailable.copy(userMessageKey = " ") }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2ContractFixtures.readyCapability.copy(
                modelVersion = "",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2ContractFixtures.readyCapability.copy(
                taxonomyVersion = " ",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2ContractFixtures.readyCapability.copy(
                calibrationVersion = "",
            )
        }

        assertFailsWith<IllegalArgumentException> { InferenceV2Outcome.Components(emptyList()) }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2Outcome.Components(listOf(estimatedComponent, estimatedComponent.copy()))
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2Outcome.Abstention(V2AbstentionReason.IMAGE_QUALITY, " ", provenance)
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2Outcome.Failure(V2InferenceFailureCode.INVALID_INPUT, false, " ", provenance)
        }
        assertFailsWith<IllegalArgumentException> { estimatedComponent.candidate.copy(candidateId = " ") }
        assertFailsWith<IllegalArgumentException> { estimatedComponent.candidate.copy(label = " ") }
        assertFailsWith<IllegalArgumentException> { CalibratedConfidence(-0.1, ConfidenceBand.LOW) }
        assertFailsWith<IllegalArgumentException> { CalibratedConfidence(1.1, ConfidenceBand.HIGH) }
        assertFailsWith<IllegalArgumentException> {
            CalibratedConfidence(
                Double.POSITIVE_INFINITY,
                ConfidenceBand.HIGH,
            )
        }
        assertFailsWith<IllegalArgumentException> { CalibratedConfidence(0.5, ConfidenceBand.LOW) }
        assertFailsWith<IllegalArgumentException> { NormalizedVisibleRegion(-0.1, 0.0, 0.1, 0.1) }
        assertFailsWith<IllegalArgumentException> { NormalizedVisibleRegion(0.0, 0.0, 0.0, 0.1) }
        assertFailsWith<IllegalArgumentException> { NormalizedVisibleRegion(0.0, 0.0, 1.1, 0.1) }
        assertFailsWith<IllegalArgumentException> { NormalizedVisibleRegion(Double.NaN, 0.0, 0.1, 0.1) }
        assertFailsWith<IllegalArgumentException> {
            MassEvidence.Estimated(
                GramRange(1.0, 2.0),
                listOf(MassCalibrationAssumption.PARTIAL_OCCLUSION, MassCalibrationAssumption.PARTIAL_OCCLUSION),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            MassEvidence.Abstained(MassAbstentionReason.IMAGE_QUALITY, " ")
        }
        assertFailsWith<IllegalArgumentException> { GramRange(Double.POSITIVE_INFINITY, 1.0) }
        assertFailsWith<IllegalArgumentException> { GramRange(0.0, -1.0) }
        assertFailsWith<IllegalArgumentException> { provenance.copy(modelVersion = " ") }
        assertFailsWith<IllegalArgumentException> { provenance.copy(taxonomyVersion = " ") }
        assertFailsWith<IllegalArgumentException> { provenance.copy(calibrationVersion = " ") }
        assertFailsWith<IllegalArgumentException> { provenance.copy(contractVersion = PHOTO_INFERENCE_CONTRACT_V1) }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2TelemetryEvent(
                contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
                eventType = InferenceTelemetryEventType.ATTEMPT,
                route = InferenceRoute.LOCAL,
                capabilityState = CapabilityState.READY,
                outcome = InferenceV2TelemetryOutcome.COMPONENTS,
                durationBucket = TelemetryDurationBucket.UNDER_1S,
                resourceBucket = TelemetryResourceBucket.WITHIN_BUDGET,
                componentCountBucket = ComponentCountBucket.ZERO,
            )
        }

        MassCalibrationAssumption.entries.forEach { assumption ->
            val result = InferenceV2ContractFixtures.estimatedPhotoResult.copy(
                outcome = InferenceV2Outcome.Components(
                    listOf(
                        estimatedComponent.copy(mass = MassEvidence.Estimated(GramRange(1.0, 2.0), listOf(assumption))),
                    ),
                ),
            )
            assertEquals(result, InferenceV2ContractJson.decode(InferenceV2ContractJson.encode(result)))
        }
        MassNotEvaluableReason.entries.forEach { reason ->
            val result = InferenceV2ContractFixtures.notEvaluablePhotoResult.copy(
                outcome = InferenceV2Outcome.Components(
                    listOf(
                        estimatedComponent.copy(
                            mass = MassEvidence.NotEvaluable(reason, "inference.mass.not_evaluable"),
                        ),
                    ),
                ),
            )
            assertEquals(result, InferenceV2ContractJson.decode(InferenceV2ContractJson.encode(result)))
        }
        MassAbstentionReason.entries.forEach { reason ->
            val result = InferenceV2ContractFixtures.estimatedPhotoResult.copy(
                outcome = InferenceV2Outcome.Components(
                    listOf(estimatedComponent.copy(mass = MassEvidence.Abstained(reason, "inference.mass.abstained"))),
                ),
            )
            assertEquals(result, InferenceV2ContractJson.decode(InferenceV2ContractJson.encode(result)))
        }
        V2AbstentionReason.entries.forEach { reason ->
            val result = InferenceV2ContractFixtures.abstentionResult.copy(
                outcome = InferenceV2Outcome.Abstention(reason, "inference.abstention.typed", provenance),
            )
            assertEquals(result, InferenceV2ContractJson.decode(InferenceV2ContractJson.encode(result)))
        }
        V2InferenceFailureCode.entries.forEach { code ->
            val result = InferenceV2ContractFixtures.failureResult.copy(
                outcome = InferenceV2Outcome.Failure(code, false, "inference.failure.typed", provenance),
            )
            assertEquals(result, InferenceV2ContractJson.decode(InferenceV2ContractJson.encode(result)))
        }
        InferenceV2TelemetryOutcome.entries.forEach { outcome ->
            ComponentCountBucket.entries.forEach { count ->
                val event = InferenceV2ContractFixtures.telemetryEvent.copy(
                    outcome = outcome,
                    componentCountBucket = count,
                )
                assertEquals(
                    event,
                    InferenceV2ContractJson.decodeTelemetry(InferenceV2ContractJson.encodeTelemetry(event)),
                )
            }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    @Test
    fun `v2 serializable optional and boundary variants round trip`() {
        val component = (InferenceV2ContractFixtures.estimatedPhotoResult.outcome as InferenceV2Outcome.Components)
            .components
            .single()
        val withoutRegion = InferenceV2ContractJson.encode(InferenceV2ContractFixtures.estimatedPhotoResult)
            .replace("\"region\":{\"left\":0.1,\"top\":0.2,\"width\":0.3,\"height\":0.4},", "")
        assertEquals(
            null,
            (InferenceV2ContractJson.decode(withoutRegion).outcome as InferenceV2Outcome.Components)
                .components
                .single()
                .region,
        )
        assertEquals(
            CapabilityState.MODEL_REQUIRED,
            InferenceV2ContractJson.decodeCapability(
                """
                    {"contractVersion":"inference-evidence/v2","state":"model-required","reason":"model-missing",
                    "userMessageKey":"inference.capability.model_required","engineId":"fixture-component-engine",
                    "modelVersion":"unavailable","taxonomyVersion":"unavailable","calibrationVersion":"unavailable"}
                """.trimIndent().replace("\n", ""),
            ).state,
        )

        CapabilityState.entries.forEach { state ->
            val capability = if (state == CapabilityState.READY) {
                InferenceV2ContractFixtures.readyCapability
            } else {
                InferenceV2Capability(
                    contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
                    state = state,
                    reason = CapabilityReason.MODEL_MISSING,
                    userMessageKey = "inference.capability.typed",
                    engineId = "fixture-component-engine",
                    modelVersion = "unavailable",
                    taxonomyVersion = "unavailable",
                    calibrationVersion = "unavailable",
                )
            }
            assertEquals(
                capability,
                InferenceV2ContractJson.decodeCapability(InferenceV2ContractJson.encodeCapability(capability)),
            )
        }
        CapabilityReason.entries.filterNot { it == CapabilityReason.NONE }.forEach { reason ->
            val capability = InferenceV2Capability(
                contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
                state = CapabilityState.UNAVAILABLE,
                reason = reason,
                userMessageKey = "inference.capability.reason",
                engineId = "fixture-component-engine",
                modelVersion = "unavailable",
                taxonomyVersion = "unavailable",
                calibrationVersion = "unavailable",
            )
            assertEquals(
                capability,
                InferenceV2ContractJson.decodeCapability(InferenceV2ContractJson.encodeCapability(capability)),
            )
        }
        InferenceTelemetryEventType.entries.forEach { eventType ->
            InferenceRoute.entries.forEach { route ->
                CapabilityState.entries.forEach { state ->
                    val event = InferenceV2ContractFixtures.telemetryEvent.copy(
                        eventType = eventType,
                        route = route,
                        capabilityState = state,
                    )
                    assertEquals(
                        event,
                        InferenceV2ContractJson.decodeTelemetry(InferenceV2ContractJson.encodeTelemetry(event)),
                    )
                }
            }
        }
        TelemetryDurationBucket.entries.forEach { duration ->
            TelemetryResourceBucket.entries.forEach { resource ->
                val event = InferenceV2ContractFixtures.telemetryEvent.copy(
                    durationBucket = duration,
                    resourceBucket = resource,
                )
                assertEquals(
                    event,
                    InferenceV2ContractJson.decodeTelemetry(InferenceV2ContractJson.encodeTelemetry(event)),
                )
            }
        }
        ComponentVisibility.entries.forEach { visibility ->
            ComponentAmbiguity.entries.forEach { ambiguity ->
                ConfidenceBand.entries.forEach { band ->
                    val score = when (band) {
                        ConfidenceBand.LOW -> 0.0
                        ConfidenceBand.MEDIUM -> 0.5
                        ConfidenceBand.HIGH -> 0.8
                    }
                    val result = InferenceV2ContractFixtures.estimatedPhotoResult.copy(
                        outcome = InferenceV2Outcome.Components(
                            listOf(
                                component.copy(
                                    confidence = CalibratedConfidence(score, band),
                                    visibility = visibility,
                                    ambiguity = ambiguity,
                                ),
                            ),
                        ),
                    )
                    assertEquals(result, InferenceV2ContractJson.decode(InferenceV2ContractJson.encode(result)))
                }
            }
        }

        listOf(
            NormalizedVisibleRegion(0.0, 0.0, 1.0, 1.0),
            NormalizedVisibleRegion(0.0, 0.9, 0.1, 0.1),
            NormalizedVisibleRegion(0.9, 0.0, 0.1, 0.1),
        ).forEach { region ->
            val decodedRegion = (
                InferenceV2ContractJson.decode(
                    InferenceV2ContractJson.encode(
                        InferenceV2ContractFixtures.estimatedPhotoResult.copy(
                            outcome = InferenceV2Outcome.Components(listOf(component.copy(region = region))),
                        ),
                    ),
                ).outcome as InferenceV2Outcome.Components
                ).components.single().region
            assertEquals(region, decodedRegion)
        }
        assertFailsWith<IllegalArgumentException> { NormalizedVisibleRegion(0.0, -0.1, 0.1, 0.1) }
        assertFailsWith<IllegalArgumentException> { NormalizedVisibleRegion(0.0, 0.0, 0.1, 0.0) }
        assertFailsWith<IllegalArgumentException> { NormalizedVisibleRegion(0.0, 0.0, 0.1, 1.1) }
        assertFailsWith<IllegalArgumentException> { NormalizedVisibleRegion(0.0, 0.9, 0.1, 0.2) }
        assertFailsWith<IllegalArgumentException> { GramRange(0.0, Double.POSITIVE_INFINITY) }
        assertFailsWith<IllegalArgumentException> { GramRange(-1.0, 1.0) }
        assertFailsWith<IllegalArgumentException> { GramRange(1.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { CalibratedConfidence(Double.NEGATIVE_INFINITY, ConfidenceBand.LOW) }
        assertFailsWith<IllegalArgumentException> { CalibratedConfidence(0.0, ConfidenceBand.MEDIUM) }
        assertFailsWith<IllegalArgumentException> { CalibratedConfidence(0.8, ConfidenceBand.MEDIUM) }
    }

    @Suppress("LongMethod")
    @Test
    fun `v2 every validation predicate has an independently invalid example`() {
        val attempt = AttemptId("attempt-v2-equality")
        val photo = InferenceV2Input.Photo(PhotoRef.issue(attempt, "leased-v2-equality"))
        val component = (InferenceV2ContractFixtures.estimatedPhotoResult.outcome as InferenceV2Outcome.Components)
            .components
            .single()
        val provenance = component.provenance

        assertEquals(photo, InferenceV2Input.Photo(PhotoRef.issue(attempt, "leased-v2-equality")))
        assertFalse(photo == InferenceV2Input.Photo(PhotoRef.issue(attempt, "other-v2-photo")))
        assertFalse(photo.equals("photo"))
        assertEquals(photo.hashCode(), InferenceV2Input.Photo(PhotoRef.issue(attempt, "leased-v2-equality")).hashCode())
        assertFalse(InferenceV2Input.Text("beans", "en") == InferenceV2Input.Text("beans", "fr"))
        assertFalse(InferenceV2Input.Text("beans", "en").equals("beans"))
        assertEquals(
            InferenceV2Input.Text("beans", "en").hashCode(),
            InferenceV2Input.Text("beans", "en").hashCode(),
        )
        assertIs<InferenceV2InputResolution.Invalid>(
            InferenceV2Input.resolve(photoRef = null, text = " ", inputLocale = "en"),
        ).also { assertEquals(InferenceV2InputError.TEXT_VALUE_REQUIRED, it.error) }
        assertIs<InferenceV2InputResolution.Invalid>(
            InferenceV2Input.resolve(photoRef = null, text = "x".repeat(2_001), inputLocale = "en"),
        ).also { assertEquals(InferenceV2InputError.TEXT_VALUE_TOO_LONG, it.error) }
        assertFailsWith<IllegalArgumentException> { requireV2Identifier(" ", "test") }
        assertFailsWith<IllegalArgumentException> { requireV2Locale("invalid_locale", "test") }
        assertFailsWith<IllegalArgumentException> { requireContractV2(PHOTO_INFERENCE_CONTRACT_V1, "test") }

        assertFailsWith<IllegalArgumentException> {
            InferenceV2Capability(
                contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
                state = CapabilityState.MODEL_REQUIRED,
                reason = CapabilityReason.MODEL_MISSING,
                userMessageKey = "inference.capability.invalid",
                engineId = " ",
                modelVersion = "",
                taxonomyVersion = "",
                calibrationVersion = "",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2Result(
                contractVersion = PHOTO_INFERENCE_CONTRACT_V1,
                inputModality = InferenceInputModality.PHOTO,
                outcome = InferenceV2ContractFixtures.estimatedPhotoResult.outcome,
            )
        }
        assertEquals(
            2,
            InferenceV2Outcome.Components(
                listOf(component, component.copy(componentId = "component:salmon-2")),
            ).components.size,
        )

        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { score ->
            assertFailsWith<IllegalArgumentException> { CalibratedConfidence(score, ConfidenceBand.LOW) }
        }
        assertFailsWith<IllegalArgumentException> { CalibratedConfidence(1.0, ConfidenceBand.MEDIUM) }
        listOf(
            doubleArrayOf(Double.NaN, 0.0, 0.1, 0.1),
            doubleArrayOf(0.0, Double.NaN, 0.1, 0.1),
            doubleArrayOf(0.0, 0.0, Double.NaN, 0.1),
            doubleArrayOf(0.0, 0.0, 0.1, Double.NaN),
            doubleArrayOf(1.1, 0.0, 0.1, 0.1),
            doubleArrayOf(0.0, 1.1, 0.1, 0.1),
            doubleArrayOf(0.0, 0.0, -0.1, 0.1),
            doubleArrayOf(0.0, 0.0, 0.1, -0.1),
        ).forEach { values ->
            assertFailsWith<IllegalArgumentException> {
                NormalizedVisibleRegion(values[0], values[1], values[2], values[3])
            }
        }
        listOf(
            doubleArrayOf(Double.NaN, 0.0),
            doubleArrayOf(0.0, Double.NaN),
            doubleArrayOf(-1.0, 0.0),
            doubleArrayOf(0.0, -1.0),
        ).forEach { values ->
            assertFailsWith<IllegalArgumentException> { GramRange(values[0], values[1]) }
        }
        assertEquals(
            2,
            MassEvidence.Estimated(
                GramRange(1.0, 2.0),
                listOf(
                    MassCalibrationAssumption.SINGLE_VIEW_APPROXIMATION,
                    MassCalibrationAssumption.REFERENCE_GEOMETRY_AVAILABLE,
                ),
            ).calibrationAssumptions.size,
        )
        assertFailsWith<IllegalArgumentException> {
            MassEvidence.NotEvaluable(MassNotEvaluableReason.MISSING_CALIBRATION, " ")
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2Provenance(
                route = InferenceRoute.LOCAL,
                engineId = "bad id!",
                modelVersion = "model",
                taxonomyVersion = "taxonomy",
                calibrationVersion = "calibration",
                contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2Provenance(
                route = InferenceRoute.LOCAL,
                engineId = "engine",
                modelVersion = "model",
                taxonomyVersion = "taxonomy",
                calibrationVersion = "calibration",
                contractVersion = "inference-evidence/v3",
            )
        }
        assertEquals(
            component,
            InferenceV2ContractJson.decode(
                InferenceV2ContractJson.encode(InferenceV2ContractFixtures.estimatedPhotoResult),
            ).let { (it.outcome as InferenceV2Outcome.Components).components.single() },
        )
        assertFailsWith<SerializationException> {
            InferenceV2ContractJson.decodeCapability(
                InferenceV2ContractJson.encodeCapability(InferenceV2ContractFixtures.readyCapability)
                    .replace("\"engineId\":", "\"removedEngineId\":"),
            )
        }
        assertFailsWith<SerializationException> {
            InferenceV2ContractJson.decodeTelemetry(
                InferenceV2ContractJson.encodeTelemetry(InferenceV2ContractFixtures.telemetryEvent)
                    .replace("\"componentCountBucket\":", "\"removedComponentCountBucket\":"),
            )
        }
        assertFailsWith<SerializationException> {
            InferenceV2ContractJson.decode(
                InferenceV2ContractJson.encode(InferenceV2ContractFixtures.estimatedPhotoResult)
                    .replace("\"estimated\"", "\"exact\""),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            InferenceV2Result(
                contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
                inputModality = InferenceInputModality.TEXT,
                outcome = InferenceV2Outcome.Components(
                    listOf(
                        component.copy(
                            mass = MassEvidence.Abstained(
                                MassAbstentionReason.IMAGE_QUALITY,
                                "inference.mass.abstained",
                            ),
                        ),
                    ),
                ),
            )
        }
        assertEquals(
            provenance,
            (InferenceV2ContractFixtures.abstentionResult.outcome as InferenceV2Outcome.Abstention).provenance,
        )
    }

    @Suppress("LongMethod")
    @Test
    fun `v2 evidence equality distinguishes every allowed field`() {
        val component = (InferenceV2ContractFixtures.estimatedPhotoResult.outcome as InferenceV2Outcome.Components)
            .components
            .single()
        val capability = InferenceV2Capability(
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
            state = CapabilityState.UNAVAILABLE,
            reason = CapabilityReason.MODEL_MISSING,
            userMessageKey = "inference.capability.equality",
            engineId = "fixture-component-engine",
            modelVersion = "model-1",
            taxonomyVersion = "taxonomy-1",
            calibrationVersion = "calibration-1",
        )
        assertEquals(capability, capability.copy())
        assertEquals(capability, capability.copy(contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2))
        assertFalse(capability == capability.copy(state = CapabilityState.UNSUPPORTED))
        assertFalse(capability == capability.copy(reason = CapabilityReason.DEVICE_UNSUPPORTED))
        assertFalse(capability == capability.copy(userMessageKey = "inference.capability.other"))
        assertFalse(capability == capability.copy(engineId = "other-engine"))
        assertFalse(capability == capability.copy(modelVersion = "model-2"))
        assertFalse(capability == capability.copy(taxonomyVersion = "taxonomy-2"))
        assertFalse(capability == capability.copy(calibrationVersion = "calibration-2"))
        assertFalse(capability.equals("capability"))
        val unavailableCapability = InferenceV2Capability(
            contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2,
            state = CapabilityState.MODEL_REQUIRED,
            reason = CapabilityReason.MODEL_MISSING,
            userMessageKey = "inference.capability.unavailable",
            engineId = "fixture-component-engine",
            modelVersion = "unavailable",
            taxonomyVersion = "unavailable",
            calibrationVersion = "unavailable",
        )
        assertEquals(
            unavailableCapability,
            InferenceV2ContractJson.decodeCapability(InferenceV2ContractJson.encodeCapability(unavailableCapability)),
        )

        assertEquals(component, component.copy())
        assertFalse(component == component.copy(componentId = "component:salmon-2"))
        assertFalse(component == component.copy(candidate = component.candidate.copy(label = "Other salmon")))
        assertFalse(component == component.copy(confidence = CalibratedConfidence(0.8, ConfidenceBand.HIGH)))
        assertFalse(component == component.copy(visibility = ComponentVisibility.PARTIALLY_OCCLUDED))
        assertFalse(component == component.copy(ambiguity = ComponentAmbiguity.MIXED_CONTENT))
        assertFalse(component == component.copy(region = null))
        assertFalse(
            component == component.copy(
                mass = MassEvidence.NotEvaluable(
                    MassNotEvaluableReason.AMBIGUOUS_PORTION,
                    "inference.mass.ambiguous_portion",
                ),
            ),
        )
        assertFalse(component == component.copy(provenance = component.provenance.copy(route = InferenceRoute.REMOTE)))
        assertFalse(component.equals("component"))
        val componentWithoutRegion = VisibleComponent(
            componentId = component.componentId,
            candidate = component.candidate,
            confidence = component.confidence,
            visibility = component.visibility,
            ambiguity = component.ambiguity,
            mass = component.mass,
            provenance = component.provenance,
        )
        assertEquals(null, componentWithoutRegion.region)

        val candidate = component.candidate
        assertEquals(candidate, candidate.copy())
        assertFalse(candidate == candidate.copy(candidateId = "ingredient:other"))
        assertFalse(candidate == candidate.copy(label = "Other"))
        assertFalse(candidate == candidate.copy(locale = "fr"))
        assertFalse(candidate.equals("candidate"))

        val confidence = component.confidence
        assertEquals(confidence, confidence.copy())
        assertEquals(confidence, confidence.copy(score = confidence.score))
        assertEquals(confidence, confidence.copy(band = confidence.band))

        val region = component.region!!
        assertEquals(region, region.copy())
        assertFalse(region == region.copy(left = 0.2))
        assertFalse(region == region.copy(top = 0.3))
        assertFalse(region == region.copy(width = 0.2))
        assertFalse(region == region.copy(height = 0.3))
        assertFalse(region.equals("region"))

        val range = (component.mass as MassEvidence.Estimated).range
        assertEquals(range, range.copy())
        assertFalse(range == range.copy(lowerGrams = 119.0))
        assertFalse(range == range.copy(upperGrams = 156.0))
        assertFalse(range.equals("range"))

        val provenance = component.provenance
        assertEquals(provenance, provenance.copy())
        assertFalse(provenance == provenance.copy(route = InferenceRoute.REMOTE))
        assertFalse(provenance == provenance.copy(engineId = "other-engine"))
        assertFalse(provenance == provenance.copy(modelVersion = "other-model"))
        assertFalse(provenance == provenance.copy(taxonomyVersion = "other-taxonomy"))
        assertFalse(provenance == provenance.copy(calibrationVersion = "other-calibration"))
        assertEquals(provenance, provenance.copy(contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2))
        assertFalse(provenance.equals("provenance"))

        val estimatedMass = component.mass as MassEvidence.Estimated
        assertEquals(estimatedMass, estimatedMass.copy())
        assertEquals(estimatedMass, estimatedMass.copy(range = estimatedMass.range))
        assertEquals(estimatedMass, estimatedMass.copy(calibrationAssumptions = estimatedMass.calibrationAssumptions))
        val notEvaluableMass = MassEvidence.NotEvaluable(
            reason = MassNotEvaluableReason.MISSING_CALIBRATION,
            userMessageKey = "inference.mass.missing_calibration",
        )
        assertEquals(notEvaluableMass, notEvaluableMass.copy())
        assertEquals(notEvaluableMass, notEvaluableMass.copy(reason = notEvaluableMass.reason))
        assertEquals(notEvaluableMass, notEvaluableMass.copy(userMessageKey = notEvaluableMass.userMessageKey))
        val abstainedMass = MassEvidence.Abstained(
            reason = MassAbstentionReason.IMAGE_QUALITY,
            userMessageKey = "inference.mass.abstained",
        )
        assertEquals(abstainedMass, abstainedMass.copy())
        assertEquals(abstainedMass, abstainedMass.copy(reason = abstainedMass.reason))
        assertEquals(abstainedMass, abstainedMass.copy(userMessageKey = abstainedMass.userMessageKey))

        val componentsOutcome = InferenceV2Outcome.Components(listOf(component))
        assertEquals(componentsOutcome, componentsOutcome.copy())
        assertEquals(componentsOutcome, componentsOutcome.copy(components = componentsOutcome.components))
        val abstentionOutcome = InferenceV2ContractFixtures.abstentionResult.outcome as InferenceV2Outcome.Abstention
        assertEquals(abstentionOutcome, abstentionOutcome.copy())
        assertEquals(abstentionOutcome, abstentionOutcome.copy(reason = abstentionOutcome.reason))
        assertEquals(abstentionOutcome, abstentionOutcome.copy(userMessageKey = abstentionOutcome.userMessageKey))
        assertEquals(abstentionOutcome, abstentionOutcome.copy(provenance = abstentionOutcome.provenance))
        val failureOutcome = InferenceV2ContractFixtures.failureResult.outcome as InferenceV2Outcome.Failure
        assertEquals(failureOutcome, failureOutcome.copy())
        assertEquals(failureOutcome, failureOutcome.copy(code = failureOutcome.code))
        assertEquals(failureOutcome, failureOutcome.copy(retryable = failureOutcome.retryable))
        assertEquals(failureOutcome, failureOutcome.copy(userMessageKey = failureOutcome.userMessageKey))
        assertEquals(failureOutcome, failureOutcome.copy(provenance = failureOutcome.provenance))

        val failureResult = InferenceV2ContractFixtures.failureResult
        assertEquals(failureResult, failureResult.copy())
        assertEquals(failureResult, failureResult.copy(contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2))
        assertEquals(failureResult, failureResult.copy(inputModality = InferenceInputModality.PHOTO))
        assertEquals(failureResult, failureResult.copy(outcome = failureResult.outcome))

        val telemetry = InferenceV2ContractFixtures.telemetryEvent
        assertEquals(telemetry, telemetry.copy())
        assertEquals(telemetry, telemetry.copy(contractVersion = INFERENCE_EVIDENCE_CONTRACT_V2))
        assertFalse(telemetry == telemetry.copy(eventType = InferenceTelemetryEventType.CAPABILITY))
        assertFalse(telemetry == telemetry.copy(route = InferenceRoute.REMOTE))
        assertFalse(telemetry == telemetry.copy(capabilityState = CapabilityState.UNSUPPORTED))
        assertFalse(telemetry == telemetry.copy(outcome = InferenceV2TelemetryOutcome.CANCELLED))
        assertFalse(telemetry == telemetry.copy(durationBucket = TelemetryDurationBucket.OVER_5S))
        assertFalse(telemetry == telemetry.copy(resourceBucket = TelemetryResourceBucket.OVER_BUDGET))
        assertFalse(telemetry == telemetry.copy(componentCountBucket = ComponentCountBucket.FIVE_OR_MORE))
        assertFalse(telemetry.equals("telemetry"))
    }

    @Suppress("LongMethod")
    @Test
    fun `v2 decoders reject every required released evidence field`() {
        val estimated = InferenceV2ContractJson.encode(InferenceV2ContractFixtures.estimatedPhotoResult)
        listOf(
            "contractVersion",
            "inputModality",
            "outcome",
            "componentId",
            "candidate",
            "confidence",
            "visibility",
            "ambiguity",
            "mass",
            "provenance",
            "candidateId",
            "label",
            "locale",
            "score",
            "band",
            "left",
            "top",
            "width",
            "height",
            "massType",
            "range",
            "calibrationAssumptions",
            "lowerGrams",
            "upperGrams",
            "route",
            "engineId",
            "modelVersion",
            "taxonomyVersion",
            "calibrationVersion",
        ).forEach { field ->
            assertFailsWith<SerializationException> {
                InferenceV2ContractJson.decode(estimated.replaceFirst("\"$field\":", "\"removed$field\":"))
            }
        }
        listOf(
            InferenceV2ContractJson.encode(InferenceV2ContractFixtures.abstentionResult) to listOf(
                "reason",
                "userMessageKey",
                "provenance",
            ),
            InferenceV2ContractJson.encode(InferenceV2ContractFixtures.failureResult) to listOf(
                "code",
                "retryable",
                "userMessageKey",
                "provenance",
            ),
        ).forEach { (payload, fields) ->
            fields.forEach { field ->
                assertFailsWith<SerializationException> {
                    InferenceV2ContractJson.decode(payload.replaceFirst("\"$field\":", "\"removed$field\":"))
                }
            }
        }
        val capability = InferenceV2ContractJson.encodeCapability(InferenceV2ContractFixtures.readyCapability)
        listOf(
            "contractVersion",
            "state",
            "reason",
            "userMessageKey",
            "engineId",
            "modelVersion",
            "taxonomyVersion",
            "calibrationVersion",
        ).forEach { field ->
            assertFailsWith<SerializationException> {
                InferenceV2ContractJson.decodeCapability(capability.replaceFirst("\"$field\":", "\"removed$field\":"))
            }
        }
        val telemetry = InferenceV2ContractJson.encodeTelemetry(InferenceV2ContractFixtures.telemetryEvent)
        listOf(
            "contractVersion",
            "eventType",
            "route",
            "capabilityState",
            "outcome",
            "durationBucket",
            "resourceBucket",
            "componentCountBucket",
        ).forEach { field ->
            assertFailsWith<SerializationException> {
                InferenceV2ContractJson.decodeTelemetry(telemetry.replaceFirst("\"$field\":", "\"removed$field\":"))
            }
        }
    }

    @Suppress("LongMethod")
    @Test
    fun `v2 alternate decoder masks and union paths remain fail closed`() {
        val component = (InferenceV2ContractFixtures.estimatedPhotoResult.outcome as InferenceV2Outcome.Components)
            .components
            .single()
        assertIs<InferenceV2InputResolution.Invalid>(
            InferenceV2Input.resolve(photoRef = null, text = "", inputLocale = "en"),
        ).also { assertEquals(InferenceV2InputError.TEXT_VALUE_REQUIRED, it.error) }
        assertIs<InferenceV2InputResolution.Invalid>(
            InferenceV2Input.resolve(photoRef = null, text = "beans", inputLocale = null),
        ).also { assertEquals(InferenceV2InputError.TEXT_LOCALE_REQUIRED, it.error) }
        assertIs<InferenceV2InputResolution.Invalid>(
            InferenceV2Input.resolve(photoRef = null, text = "beans", inputLocale = ""),
        ).also { assertEquals(InferenceV2InputError.TEXT_LOCALE_REQUIRED, it.error) }

        listOf(
            doubleArrayOf(Double.POSITIVE_INFINITY, 0.0, 0.1, 0.1),
            doubleArrayOf(0.0, Double.POSITIVE_INFINITY, 0.1, 0.1),
            doubleArrayOf(0.0, 0.0, Double.POSITIVE_INFINITY, 0.1),
            doubleArrayOf(0.0, 0.0, 0.1, Double.POSITIVE_INFINITY),
        ).forEach { values ->
            assertFailsWith<IllegalArgumentException> {
                NormalizedVisibleRegion(values[0], values[1], values[2], values[3])
            }
        }
        assertFailsWith<IllegalArgumentException> { GramRange(Double.NEGATIVE_INFINITY, 1.0) }
        assertFailsWith<IllegalArgumentException> { GramRange(1.0, Double.NEGATIVE_INFINITY) }

        val textComponents = InferenceV2Outcome.Components(
            listOf(
                component.copy(
                    componentId = "component:text-1",
                    region = null,
                    mass = MassEvidence.NotEvaluable(
                        MassNotEvaluableReason.TEXT_HAS_NO_VISIBLE_PORTION,
                        "inference.mass.text_has_no_visible_portion",
                    ),
                ),
                component.copy(
                    componentId = "component:text-2",
                    region = null,
                    mass = MassEvidence.NotEvaluable(
                        MassNotEvaluableReason.TEXT_HAS_NO_VISIBLE_PORTION,
                        "inference.mass.text_has_no_visible_portion",
                    ),
                ),
            ),
        )
        assertEquals(
            InferenceInputModality.TEXT,
            InferenceV2Result(
                INFERENCE_EVIDENCE_CONTRACT_V2,
                InferenceInputModality.TEXT,
                textComponents,
            ).inputModality,
        )
        assertEquals(
            InferenceInputModality.TEXT,
            InferenceV2Result(
                INFERENCE_EVIDENCE_CONTRACT_V2,
                InferenceInputModality.TEXT,
                InferenceV2ContractFixtures.abstentionResult.outcome,
            ).inputModality,
        )
        assertEquals(
            InferenceInputModality.TEXT,
            InferenceV2Result(
                INFERENCE_EVIDENCE_CONTRACT_V2,
                InferenceInputModality.TEXT,
                InferenceV2ContractFixtures.failureResult.outcome,
            ).inputModality,
        )

        listOf(
            { InferenceV2ContractFixtures.readyCapability.copy(modelVersion = "") },
            { InferenceV2ContractFixtures.readyCapability.copy(modelVersion = " ") },
            { InferenceV2ContractFixtures.readyCapability.copy(taxonomyVersion = "") },
            { InferenceV2ContractFixtures.readyCapability.copy(calibrationVersion = " ") },
        ).forEach { invalidCapability ->
            assertFailsWith<IllegalArgumentException> { invalidCapability() }
        }

        val estimated = InferenceV2ContractJson.encode(InferenceV2ContractFixtures.estimatedPhotoResult)
        assertFailsWith<SerializationException> {
            InferenceV2ContractJson.decode(estimated.replaceFirst("\"components\":", "\"removedComponents\":"))
        }
        val notEvaluable = InferenceV2ContractJson.encode(InferenceV2ContractFixtures.notEvaluablePhotoResult)
        listOf("reason", "userMessageKey").forEach { field ->
            assertFailsWith<SerializationException> {
                InferenceV2ContractJson.decode(notEvaluable.replaceFirst("\"$field\":", "\"removed$field\":"))
            }
        }
        val abstained = InferenceV2ContractFixtures.estimatedPhotoResult.copy(
            outcome = InferenceV2Outcome.Components(
                listOf(
                    component.copy(
                        mass = MassEvidence.Abstained(
                            MassAbstentionReason.IMAGE_QUALITY,
                            "inference.mass.abstained",
                        ),
                    ),
                ),
            ),
        )
        val abstainedPayload = InferenceV2ContractJson.encode(abstained)
        listOf("reason", "userMessageKey").forEach { field ->
            assertFailsWith<SerializationException> {
                InferenceV2ContractJson.decode(abstainedPayload.replaceFirst("\"$field\":", "\"removed$field\":"))
            }
        }
        val capability = InferenceV2ContractJson.encodeCapability(InferenceV2ContractFixtures.readyCapability)
        assertFailsWith<SerializationException> {
            InferenceV2ContractJson.decodeCapability(capability.replace("\"ready\"", "\"unknown\""))
        }
        assertFailsWith<SerializationException> {
            InferenceV2ContractJson.decodeCapability(capability.replace("\"none\"", "\"unknown\""))
        }
    }

    @Test
    fun `v2 result and telemetry exclude nutrition and raw payloads`() {
        listOf(
            InferenceV2ContractFixtures.estimatedPhotoResult,
            InferenceV2ContractFixtures.notEvaluablePhotoResult,
            InferenceV2ContractFixtures.textNotEvaluableResult,
            InferenceV2ContractFixtures.abstentionResult,
            InferenceV2ContractFixtures.failureResult,
        ).forEach { result ->
            val payload = InferenceV2ContractJson.encode(result).lowercase()
            listOf(
                "calorie",
                "macro",
                "micronutrient",
                "nutrition",
                "raw",
                "photoPath",
                "userText",
            ).forEach { forbidden ->
                assertFalse(payload.contains(forbidden.lowercase()))
            }
        }
        val telemetry = InferenceV2ContractJson.encodeTelemetry(InferenceV2ContractFixtures.telemetryEvent)
        assertEquals(
            setOf(
                "contractVersion",
                "eventType",
                "route",
                "capabilityState",
                "outcome",
                "durationBucket",
                "resourceBucket",
                "componentCountBucket",
            ),
            Json.parseToJsonElement(telemetry).jsonObject.keys,
        )
        assertFalse(telemetry.lowercase().contains("componentid"))
        assertFalse(telemetry.lowercase().contains("grams"))
    }
}
