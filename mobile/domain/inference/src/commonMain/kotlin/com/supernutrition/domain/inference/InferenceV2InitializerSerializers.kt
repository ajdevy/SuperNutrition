package com.supernutrition.domain.inference

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
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
 * Hand-written codecs for the v2 types whose initializers carry rules.
 *
 * `@Serializable` compiles the class's `init` block twice — once into the
 * primary constructor and once into a synthetic deserialization constructor.
 * Every `require` therefore has two copies in the bytecode, and the second is
 * reachable only by decoding JSON that drives each rule to each outcome. That
 * duplicate set, not any untested rule, was the bulk of this module's uncovered
 * branch denominator: constructing a value covers one copy and leaves the other
 * permanently missed.
 *
 * Naming a serializer suppresses the generated one, so each initializer is
 * compiled once and every branch left in the denominator is one a test can
 * reach. See [ComponentModelManifestSerializer] for the same argument, and
 * `GatewayEnvelope.kt`'s `GatewayCodec` in :inference:remote for the first
 * application of it.
 *
 * Wire shape is unchanged throughout: same names, same order, same nullability.
 * Every element is encoded, so `encodeDefaults` and `explicitNulls` keep
 * deciding null handling exactly as before.
 */

internal object EngineCapabilityV2Serializer : KSerializer<EngineCapabilityV3> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.supernutrition.domain.inference.EngineCapabilityV3") {
            element<String>("contractVersion")
            element<InferenceModality>("modality")
            element<CapabilityState>("state")
            element<CapabilityReason>("reason")
            element<String>("userMessageKey")
            element<String>("engineId")
            element<String?>("modelVersion")
        }

    override fun serialize(encoder: Encoder, value: EngineCapabilityV3) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.contractVersion)
            encodeSerializableElement(descriptor, 1, InferenceModality.serializer(), value.modality)
            encodeSerializableElement(descriptor, 2, CapabilityState.serializer(), value.state)
            encodeSerializableElement(descriptor, 3, CapabilityReason.serializer(), value.reason)
            encodeStringElement(descriptor, 4, value.userMessageKey)
            encodeStringElement(descriptor, 5, value.engineId)
            encodeNullableSerializableElement(descriptor, 6, String.serializer(), value.modelVersion)
        }
    }

    override fun deserialize(decoder: Decoder): EngineCapabilityV3 = decoder.decodeStructure(descriptor) {
        var contractVersion: String? = null
        var modality: InferenceModality? = null
        var state: CapabilityState? = null
        var reason: CapabilityReason? = null
        var userMessageKey: String? = null
        var engineId: String? = null
        var modelVersion: String? = null

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break
                0 -> contractVersion = decodeStringElement(descriptor, 0)
                1 -> modality = decodeSerializableElement(descriptor, 1, InferenceModality.serializer())
                2 -> state = decodeSerializableElement(descriptor, 2, CapabilityState.serializer())
                3 -> reason = decodeSerializableElement(descriptor, 3, CapabilityReason.serializer())
                4 -> userMessageKey = decodeStringElement(descriptor, 4)
                5 -> engineId = decodeStringElement(descriptor, 5)
                6 -> modelVersion = decodeNullableSerializableElement(descriptor, 6, String.serializer())
                else -> error("Unexpected capability element index $index")
            }
        }

        EngineCapabilityV3(
            contractVersion = requireNotNull(contractVersion) { "Capability is missing contractVersion" },
            modality = requireNotNull(modality) { "Capability is missing modality" },
            state = requireNotNull(state) { "Capability is missing state" },
            reason = requireNotNull(reason) { "Capability is missing reason" },
            userMessageKey = requireNotNull(userMessageKey) { "Capability is missing userMessageKey" },
            engineId = requireNotNull(engineId) { "Capability is missing engineId" },
            modelVersion = modelVersion,
        )
    }
}

internal object InferenceItemGroupSerializer : KSerializer<InferenceItemGroup> {

    private val candidatesSerializer = ListSerializer(InferenceCandidateV3.serializer())

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.supernutrition.domain.inference.InferenceItemGroup") {
            element<String>("groupId")
            element<InferenceSourceSpan?>("sourceSpan")
            element("candidates", candidatesSerializer.descriptor)
        }

    override fun serialize(encoder: Encoder, value: InferenceItemGroup) {
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.groupId)
            encodeNullableSerializableElement(
                descriptor,
                1,
                InferenceSourceSpan.serializer(),
                value.sourceSpan,
            )
            encodeSerializableElement(descriptor, 2, candidatesSerializer, value.candidates)
        }
    }

    override fun deserialize(decoder: Decoder): InferenceItemGroup = decoder.decodeStructure(descriptor) {
        var groupId: String? = null
        var sourceSpan: InferenceSourceSpan? = null
        var candidates: List<InferenceCandidateV3>? = null

        while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                CompositeDecoder.DECODE_DONE -> break

                0 -> groupId = decodeStringElement(descriptor, 0)

                1 ->
                    sourceSpan =
                        decodeNullableSerializableElement(descriptor, 1, InferenceSourceSpan.serializer())

                2 -> candidates = decodeSerializableElement(descriptor, 2, candidatesSerializer)

                else -> error("Unexpected item group element index $index")
            }
        }

        InferenceItemGroup(
            groupId = requireNotNull(groupId) { "Item group is missing groupId" },
            sourceSpan = sourceSpan,
            candidates = requireNotNull(candidates) { "Item group is missing candidates" },
        )
    }
}
