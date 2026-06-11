package com.movit.core.training.config

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Mirrors legacy Gson [StateMessageValueTypeAdapter]:
 * objects with `up`/`down` keys deserialize as [StateMessageValue.ZoneSpecific], else [StateMessageValue.Single].
 */
object StateMessageValueSerializer : KSerializer<StateMessageValue> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("StateMessageValue")

    override fun deserialize(decoder: Decoder): StateMessageValue {
        val input = decoder as? JsonDecoder
            ?: error("StateMessageValueSerializer requires JsonDecoder")
        return when (val element = input.decodeJsonElement()) {
            is JsonObject -> {
                if (element.containsKey("up") || element.containsKey("down")) {
                    StateMessageValue.ZoneSpecific(
                        up = element["up"]?.let { input.json.decodeFromJsonElement(LocalizedText.serializer(), it) },
                        down = element["down"]?.let { input.json.decodeFromJsonElement(LocalizedText.serializer(), it) },
                    )
                } else {
                    StateMessageValue.Single(input.json.decodeFromJsonElement(LocalizedText.serializer(), element))
                }
            }
            else -> error("Expected JSON object for StateMessageValue")
        }
    }

    override fun serialize(encoder: Encoder, value: StateMessageValue) {
        val output = encoder as? JsonEncoder
            ?: error("StateMessageValueSerializer requires JsonEncoder")
        val element: JsonElement = when (value) {
            is StateMessageValue.Single ->
                output.json.encodeToJsonElement(LocalizedText.serializer(), value.message)
            is StateMessageValue.ZoneSpecific -> {
                val fields = buildMap {
                    value.up?.let { put("up", output.json.encodeToJsonElement(LocalizedText.serializer(), it)) }
                    value.down?.let { put("down", output.json.encodeToJsonElement(LocalizedText.serializer(), it)) }
                }
                JsonObject(fields)
            }
        }
        output.encodeJsonElement(element)
    }
}
