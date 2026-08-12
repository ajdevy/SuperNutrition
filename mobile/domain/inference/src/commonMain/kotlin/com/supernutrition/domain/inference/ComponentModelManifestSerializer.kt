package com.supernutrition.domain.inference

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

/**
 * Hand-written codec for [ComponentModelManifest].
 *
 * WHY THIS EXISTS. `@Serializable` emits a second copy of the class's `init`
 * block inside a synthetic deserialization constructor. Every `require` in that
 * initializer therefore appears twice in the bytecode, and the second copy is
 * reachable only by decoding JSON that drives each rule to each outcome — a
 * combinatorial set no honest test enumerates. Those duplicate branches, not any
 * untested rule, were the bulk of this module's uncovered branch denominator.
 * Naming a serializer suppresses the generated one, so the initializer is
 * compiled once and every branch in the denominator is one a test can reach.
 *
 * The wire shape is unchanged: same field names, same declaration order, same
 * nullability. Whether a null is written or omitted stays a property of the Json
 * instance — every element is encoded, so `explicitNulls` and `encodeDefaults`
 * behave exactly as they did.
 */
internal object ComponentModelManifestSerializer : KSerializer<ComponentModelManifest> {

    private const val FIELD_COUNT = 21
    private const val OWNERSHIP_INDEX = 12
    private const val ACTIVATION_INDEX = 13

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.supernutrition.domain.inference.ComponentModelManifest") {
            element<String>("manifestVersion")
            element<String>("modelVersion")
            element<String>("artifactDigest")
            element<String>("taxonomyVersion")
            element<String>("taxonomyDigest")
            element<String>("preprocessingVersion")
            element<String>("postprocessingVersion")
            element<String>("maskContractVersion")
            element<String>("calibrationVersion")
            element<String>("runtime")
            element<String>("executionProvider")
            element<String>("compatibilityTier")
            element<ComponentModelOwnership>("ownership")
            element<ComponentModelActivation>("activation")
            element<String?>("rollbackTargetVersion")
            element<String?>("productApprovalRef")
            element<String?>("engineeringApprovalRef")
            element<String?>("legalApprovalRef")
            element<String?>("corpusRef")
            element<String?>("benchmarkEvidenceRef")
            element<String?>("rollbackEvidenceRef")
        }

    override fun serialize(encoder: Encoder, value: ComponentModelManifest) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.manifestVersion)
            encodeStringElement(descriptor, 1, value.modelVersion)
            encodeStringElement(descriptor, 2, value.artifactDigest)
            encodeStringElement(descriptor, 3, value.taxonomyVersion)
            encodeStringElement(descriptor, 4, value.taxonomyDigest)
            encodeStringElement(descriptor, 5, value.preprocessingVersion)
            encodeStringElement(descriptor, 6, value.postprocessingVersion)
            encodeStringElement(descriptor, 7, value.maskContractVersion)
            encodeStringElement(descriptor, 8, value.calibrationVersion)
            encodeStringElement(descriptor, 9, value.runtime)
            encodeStringElement(descriptor, 10, value.executionProvider)
            encodeStringElement(descriptor, 11, value.compatibilityTier)
            encodeSerializableElement(descriptor, 12, ComponentModelOwnership.serializer(), value.ownership)
            encodeSerializableElement(descriptor, 13, ComponentModelActivation.serializer(), value.activation)
            encodeNullableSerializableElement(descriptor, 14, String.serializer(), value.rollbackTargetVersion)
            encodeNullableSerializableElement(descriptor, 15, String.serializer(), value.productApprovalRef)
            encodeNullableSerializableElement(descriptor, 16, String.serializer(), value.engineeringApprovalRef)
            encodeNullableSerializableElement(descriptor, 17, String.serializer(), value.legalApprovalRef)
            encodeNullableSerializableElement(descriptor, 18, String.serializer(), value.corpusRef)
            encodeNullableSerializableElement(descriptor, 19, String.serializer(), value.benchmarkEvidenceRef)
            encodeNullableSerializableElement(descriptor, 20, String.serializer(), value.rollbackEvidenceRef)
        }
    }

    override fun deserialize(decoder: Decoder): ComponentModelManifest = decoder.decodeStructure(descriptor) {
        val fields = ManifestFields()
        while (true) {
            val index = decodeElementIndex(descriptor)
            if (index == CompositeDecoder.DECODE_DONE) break
            readElement(index, fields)
        }
        fields.toManifest()
    }

    /**
     * One element, by index.
     *
     * Split out of [deserialize] rather than suppressed: the loop and the
     * per-element dispatch are two decisions, and reading them together is what
     * made the method complex. Nothing here is a contract constraint, so there is
     * no reason the rule could not simply be obeyed.
     */
    private fun CompositeDecoder.readElement(index: Int, fields: ManifestFields) {
        when (index) {
            OWNERSHIP_INDEX ->
                fields.ownership = decodeSerializableElement(
                    descriptor,
                    OWNERSHIP_INDEX,
                    ComponentModelOwnership.serializer(),
                )

            ACTIVATION_INDEX ->
                fields.activation = decodeSerializableElement(
                    descriptor,
                    ACTIVATION_INDEX,
                    ComponentModelActivation.serializer(),
                )

            in 0 until OWNERSHIP_INDEX -> fields.strings[index] = decodeStringElement(descriptor, index)

            in (ACTIVATION_INDEX + 1) until FIELD_COUNT ->
                fields.strings[index] = decodeNullableSerializableElement(descriptor, index, String.serializer())

            else -> error("Unexpected component manifest element index $index")
        }
    }

    private class ManifestFields {
        val strings = arrayOfNulls<String>(FIELD_COUNT)
        var ownership: ComponentModelOwnership? = null
        var activation: ComponentModelActivation? = null

        fun toManifest() = ComponentModelManifest(
            manifestVersion = required(strings, 0),
            modelVersion = required(strings, 1),
            artifactDigest = required(strings, 2),
            taxonomyVersion = required(strings, 3),
            taxonomyDigest = required(strings, 4),
            preprocessingVersion = required(strings, 5),
            postprocessingVersion = required(strings, 6),
            maskContractVersion = required(strings, 7),
            calibrationVersion = required(strings, 8),
            runtime = required(strings, 9),
            executionProvider = required(strings, 10),
            compatibilityTier = required(strings, 11),
            ownership = requireNotNull(ownership) { "Component manifest is missing ownership" },
            activation = requireNotNull(activation) { "Component manifest is missing activation" },
            rollbackTargetVersion = strings[14],
            productApprovalRef = strings[15],
            engineeringApprovalRef = strings[16],
            legalApprovalRef = strings[17],
            corpusRef = strings[18],
            benchmarkEvidenceRef = strings[19],
            rollbackEvidenceRef = strings[20],
        )
    }
}

/** Reject-missing-required, stated once instead of per field. */
private fun required(values: Array<String?>, index: Int): String =
    requireNotNull(values[index]) { "Component manifest is missing element $index" }
