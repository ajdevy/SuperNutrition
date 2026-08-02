package com.supernutrition.domain.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The modality-neutral release this binding implements. */
const val INFERENCE_CONTRACT_V2: String = "inference-engine/v2"

/**
 * Which payload an inference request carries.
 *
 * Serial names are lowercase because the contract fixes the wire values as
 * `["photo", "text"]`. Without them the enum encodes as `PHOTO`/`TEXT` and
 * cannot round-trip the very fixtures it was written against.
 */
@Serializable
enum class InferenceModality {
    @SerialName("photo")
    PHOTO,

    @SerialName("text")
    TEXT,
}

/**
 * Kotlin binding for `contracts/inference/v2`.
 *
 * v1's `PhotoInferenceRequest` admits only a `PhotoRef`, so a text engine cannot
 * satisfy it without claiming a photo it does not have. This is the modality
 * carrier, published alongside v1 rather than replacing it: producers and
 * consumers migrate release by release, and v1 is retired only when no
 * implementation remains.
 *
 * Exactly one payload exists per request, enforced by the type rather than by
 * validation. A request carrying neither or both is unrepresentable, which is
 * stronger than rejecting it at runtime.
 */
sealed interface InferenceRequestV2 {
    val modality: InferenceModality
    val attemptContext: InferenceAttemptContext

    /**
     * Photo identification. Bytes stay behind the lease and content ports.
     *
     * `@ConsistentCopyVisibility` makes the generated `copy()` internal to match
     * the constructor. Without it `copy()` is public, which exposes a non-public
     * constructor — a warning today and a compile error in language version 2.5 —
     * and, more immediately, offered a way around the invariant below: a public
     * `copy(photoRef = other)` produced a request whose photo and attempt context
     * belonged to different attempts, which the factory exists to forbid.
     */
    @ConsistentCopyVisibility
    data class Photo internal constructor(
        val photoRef: PhotoRef,
        override val attemptContext: InferenceAttemptContext,
    ) : InferenceRequestV2 {
        override val modality: InferenceModality get() = InferenceModality.PHOTO

        init {
            // Held by the type rather than by one construction path. The check
            // lived only in `photo()`, so `copy()` and any in-module direct
            // construction skipped it.
            require(photoRef.attemptId == attemptContext.attemptId) {
                "PhotoRef and inference context must belong to the same attempt"
            }
        }
    }

    /**
     * Typed identification.
     *
     * [inputLocale] is separate from any catalog country because AD-12 keeps
     * country independent of language: a description may be typed in a language
     * the activated catalog is not written in.
     */
    class Text internal constructor(
        val description: String,
        val inputLocale: String,
        override val attemptContext: InferenceAttemptContext,
    ) : InferenceRequestV2 {
        override val modality: InferenceModality get() = InferenceModality.TEXT

        init {
            // Same reasoning as Photo: the invariant belongs to the type, not to
            // the factory that happens to be the public way in.
            require(description.isNotBlank()) { "Inference description must not be blank" }
            require(inputLocale.isNotBlank()) { "Inference input locale must not be blank" }
        }

        /**
         * Deliberately omits the description. Free text can carry more
         * identifying detail than a food photo — a dish, a restaurant, an
         * address typed by mistake — and anything that interpolates a request
         * into a log line or crash report would otherwise leak it.
         */
        override fun toString(): String = "InferenceRequestV2.Text(description=redacted, inputLocale=$inputLocale)"

        override fun equals(other: Any?): Boolean = other is Text &&
            description == other.description &&
            inputLocale == other.inputLocale &&
            attemptContext == other.attemptContext

        override fun hashCode(): Int {
            var result = description.hashCode()
            result = 31 * result + inputLocale.hashCode()
            result = 31 * result + attemptContext.hashCode()
            return result
        }
    }

    companion object {
        fun photo(photoRef: PhotoRef, attemptContext: InferenceAttemptContext): InferenceRequestV2 =
            Photo(photoRef, attemptContext)

        fun text(
            description: String,
            inputLocale: String,
            attemptContext: InferenceAttemptContext,
        ): InferenceRequestV2 = Text(description.trim(), inputLocale, attemptContext)
    }
}

/**
 * A v2 engine. One interface serves both modalities because every safety
 * property v1 established — no numeric nutrition, typed failures, abstention as
 * a first-class outcome, provenance on every result — is modality-independent.
 */
interface InferenceEngineV2 {
    /** Every implementation must explicitly bind itself to the frozen contract release. */
    val contractVersion: String

    /** Which modalities this engine can serve. An engine may support only one. */
    val supportedModalities: Set<InferenceModality>

    /**
     * What this engine can do for one modality right now.
     *
     * Per modality because an engine serving both can be ready for photos and
     * still waiting on a text model. v1's `PhotoInferenceEngine.capability()`
     * needs no argument because it has one modality to report on; this one does.
     */
    suspend fun capability(modality: InferenceModality): EngineCapabilityV2

    /**
     * Returns [InferenceResultV2] rather than v1's `PhotoInferenceResult`.
     *
     * The interface accepted a text request and could only answer in a
     * photo-shaped result, which had no way to carry the per-item spans a typed
     * description produces — so the modality this contract exists to add was
     * representable on the way in and lost on the way out.
     */
    suspend fun analyze(request: InferenceRequestV2): InferenceResultV2
}
