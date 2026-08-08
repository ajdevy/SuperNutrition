package com.supernutrition.domain.inference

const val INFERENCE_EVIDENCE_CONTRACT_V2 = "inference-evidence/v2"

private val V2_IDENTIFIER_PATTERN = Regex("[A-Za-z0-9._:-]{1,128}")
private val V2_LOCALE_PATTERN = Regex("[A-Za-z]{2,8}(-[A-Za-z0-9]{1,8})*")
private const val V2_TEXT_MAX_LENGTH = 2_000

internal fun requireV2(condition: Boolean, message: String) {
    require(condition) { message }
}

internal fun requireEvidenceContractV2(value: String, message: String): String = value.also {
    requireV2(it == INFERENCE_EVIDENCE_CONTRACT_V2, message)
}

internal fun requireV2Identifier(value: String, message: String): String = value.also {
    requireV2(V2_IDENTIFIER_PATTERN.matches(it), message)
}

internal fun requireV2Locale(value: String, message: String): String = value.also {
    requireV2(V2_LOCALE_PATTERN.matches(it), message)
}

/** A closed input boundary. Photo content remains behind PhotoRef; text is never serialised as evidence. */
sealed interface InferenceV2Input {
    val modality: InferenceInputModality

    class Photo(val photoRef: PhotoRef) : InferenceV2Input {
        override val modality: InferenceInputModality = InferenceInputModality.PHOTO

        override fun equals(other: Any?): Boolean = other is Photo && photoRef == other.photoRef

        override fun hashCode(): Int = photoRef.hashCode()

        override fun toString(): String = "InferenceV2Input.Photo(redacted)"
    }

    class Text(val value: String, val inputLocale: String) : InferenceV2Input {
        override val modality: InferenceInputModality = InferenceInputModality.TEXT

        init {
            requireV2(value.isNotBlank(), "Text inference input must not be blank")
            requireV2(value.length <= V2_TEXT_MAX_LENGTH, "Text inference input exceeds the bounded contract length")
            requireV2Locale(inputLocale, "Text inference input locale must be a bounded BCP-47-style tag")
        }

        override fun equals(other: Any?): Boolean =
            other is Text && value == other.value && inputLocale == other.inputLocale

        override fun hashCode(): Int = 31 * value.hashCode() + inputLocale.hashCode()

        override fun toString(): String = "InferenceV2Input.Text(redacted)"
    }

    companion object {
        /**
         * Adapters that receive loosely-bound inputs must resolve one modality
         * explicitly; absent or ambiguous input is a typed, fail-closed error.
         */
        fun resolve(photoRef: PhotoRef?, text: String?, inputLocale: String?): InferenceV2InputResolution = when {
            photoRef != null && text != null -> InferenceV2InputResolution.Invalid(
                InferenceV2InputError.AMBIGUOUS_INPUT,
            )

            photoRef == null && text == null -> InferenceV2InputResolution.Invalid(
                InferenceV2InputError.MISSING_INPUT,
            )

            photoRef != null && inputLocale != null -> InferenceV2InputResolution.Invalid(
                InferenceV2InputError.PHOTO_LOCALE_FORBIDDEN,
            )

            photoRef != null -> InferenceV2InputResolution.Valid(Photo(photoRef))

            text.isNullOrBlank() -> InferenceV2InputResolution.Invalid(InferenceV2InputError.TEXT_VALUE_REQUIRED)

            inputLocale.isNullOrBlank() -> InferenceV2InputResolution.Invalid(
                InferenceV2InputError.TEXT_LOCALE_REQUIRED,
            )

            text.length > V2_TEXT_MAX_LENGTH -> InferenceV2InputResolution.Invalid(
                InferenceV2InputError.TEXT_VALUE_TOO_LONG,
            )

            !V2_LOCALE_PATTERN.matches(inputLocale) -> InferenceV2InputResolution.Invalid(
                InferenceV2InputError.TEXT_LOCALE_INVALID,
            )

            else -> InferenceV2InputResolution.Valid(Text(text, inputLocale))
        }
    }
}

sealed interface InferenceV2InputResolution {
    data class Valid(val input: InferenceV2Input) : InferenceV2InputResolution

    data class Invalid(val error: InferenceV2InputError) : InferenceV2InputResolution
}

enum class InferenceV2InputError {
    MISSING_INPUT,
    AMBIGUOUS_INPUT,
    PHOTO_LOCALE_FORBIDDEN,
    TEXT_VALUE_REQUIRED,
    TEXT_VALUE_TOO_LONG,
    TEXT_LOCALE_REQUIRED,
    TEXT_LOCALE_INVALID,
}

/** v2 input bound to an immutable attempt; raw image and user text never leave this port. */
data class InferenceV2Request(val input: InferenceV2Input, val attemptContext: InferenceAttemptContext) {
    init {
        if (input is InferenceV2Input.Photo) {
            requireV2(
                input.photoRef.attemptId == attemptContext.attemptId,
                "PhotoRef and v2 inference context must belong to the same attempt",
            )
        }
    }
}

abstract class InferenceV2Engine {
    abstract val contractVersion: String

    abstract suspend fun capability(): InferenceV2Capability

    protected abstract suspend fun analyzeUnvalidated(request: InferenceV2Request): InferenceV2Result

    /** Enforces the closed request/result modality pair at the shared engine boundary. */
    suspend fun analyze(request: InferenceV2Request): InferenceV2Result = analyzeUnvalidated(request).also { result ->
        requireV2(
            result.inputModality == request.input.modality,
            "Inference v2 result modality must match its request modality",
        )
    }
}
