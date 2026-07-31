package com.supernutrition.domain.inference

/** The modality-neutral release this binding implements. */
const val INFERENCE_CONTRACT_V2: String = "inference-engine/v2"

/** Which payload an inference request carries. */
enum class InferenceModality { PHOTO, TEXT }

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

    /** Photo identification. Bytes stay behind the lease and content ports. */
    data class Photo internal constructor(
        val photoRef: PhotoRef,
        override val attemptContext: InferenceAttemptContext,
    ) : InferenceRequestV2 {
        override val modality: InferenceModality get() = InferenceModality.PHOTO
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

        /**
         * Deliberately omits the description. Free text can carry more
         * identifying detail than a food photo — a dish, a restaurant, an
         * address typed by mistake — and anything that interpolates a request
         * into a log line or crash report would otherwise leak it.
         */
        override fun toString(): String =
            "InferenceRequestV2.Text(description=redacted, inputLocale=$inputLocale)"

        override fun equals(other: Any?): Boolean =
            other is Text &&
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
        fun photo(photoRef: PhotoRef, attemptContext: InferenceAttemptContext): InferenceRequestV2 {
            require(photoRef.attemptId == attemptContext.attemptId) {
                "PhotoRef and inference context must belong to the same attempt"
            }
            return Photo(photoRef, attemptContext)
        }

        fun text(
            description: String,
            inputLocale: String,
            attemptContext: InferenceAttemptContext,
        ): InferenceRequestV2 {
            require(description.isNotBlank()) { "Inference description must not be blank" }
            require(inputLocale.isNotBlank()) { "Inference input locale must not be blank" }
            return Text(description.trim(), inputLocale, attemptContext)
        }
    }
}

/**
 * A v2 engine. One interface serves both modalities because every safety
 * property v1 established — no numeric nutrition, typed failures, abstention as
 * a first-class outcome, provenance on every result — is modality-independent.
 */
interface InferenceEngineV2 {
    /** Which modalities this engine can serve. An engine may support only one. */
    val supportedModalities: Set<InferenceModality>

    suspend fun analyze(request: InferenceRequestV2): PhotoInferenceResult
}
