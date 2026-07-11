package com.movit.core.training.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ExerciseConfigParser {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun parseConfig(element: JsonElement): ExerciseConfig =
        json.decodeFromJsonElement(ExerciseConfig.serializer(), element).sanitizeDefaults()

    fun parseConfigJson(text: String): ExerciseConfig =
        parseConfig(json.parseToJsonElement(text))

    fun parseRecord(element: JsonElement): ExerciseConfigRecord {
        val obj = element.jsonObject
        val id = obj.stringOrEmpty("id")
        val slug = obj.stringOrEmpty("slug")
        val updatedAt = obj.stringOrEmpty("updatedAt")
        val config = parseConfig(element)
        return ExerciseConfigRecord.fromConfig(
            id = id,
            slug = slug.ifBlank { inferSlugFromName(config) },
            updatedAt = updatedAt,
            config = config,
        )
    }

    fun parseRecords(elements: List<JsonElement>): List<ExerciseConfigRecord> {
        var dropped = 0
        val out = elements.mapNotNull { element ->
            runCatching { parseRecord(element) }
                .onFailure { error ->
                    dropped++
                    val slug = runCatching {
                        element.jsonObject["slug"]?.jsonPrimitive?.content
                    }.getOrNull().orEmpty().ifBlank { "<unknown>" }
                    println(
                        "[ExerciseConfigParser] dropped slug=$slug reason=${error.message ?: error::class.simpleName}",
                    )
                }
                .getOrNull()
        }
        if (dropped > 0) {
            println("[ExerciseConfigParser] dropped $dropped of ${elements.size} records")
        }
        return out
    }

    private fun JsonObject.stringOrEmpty(key: String): String =
        get(key)?.jsonPrimitive?.content.orEmpty()

    private fun inferSlugFromName(config: ExerciseConfig): String =
        config.name.en.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
