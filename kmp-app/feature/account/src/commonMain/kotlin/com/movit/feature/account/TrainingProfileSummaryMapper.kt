package com.movit.feature.account

import com.movit.core.network.dto.TrainingProfilePayloadDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

object TrainingProfileSummaryMapper {
    const val EMPTY_SUMMARY_KEY = "profile_training_profile_summary_empty"

    fun format(payload: TrainingProfilePayloadDto): String {
        val profile = payload.profile.orEmpty()
        val parts = mutableListOf<String>()

        payload.trainingGoal?.takeIf { it.isNotBlank() }?.let { goal ->
            parts.add(formatGoalCode(goal))
        }
        parseInt(profile["availableDaysPerWeek"])?.let { days ->
            parts.add("$days d/w")
        }
        parseInt(profile["maxSessionMinutes"])?.let { minutes ->
            parts.add("$minutes min")
        }
        parseStringList(profile["availableEquipment"]).takeIf { it.isNotEmpty() }?.let { equipment ->
            parts.add(equipment.joinToString(", "))
        }
        parseString(profile["trainingLocation"])?.let { location ->
            parts.add(location.replaceFirstChar { char -> char.uppercaseChar() })
        }

        return if (parts.isEmpty()) EMPTY_SUMMARY_KEY else parts.joinToString(" · ")
    }

    private fun formatGoalCode(code: String): String {
        return code
            .lowercase()
            .split('_')
            .joinToString(" ") { token ->
                token.replaceFirstChar { char -> char.uppercaseChar() }
            }
    }

    private fun parseInt(element: JsonElement?): Int? {
        if (element == null) return null
        if (element is JsonPrimitive) {
            return element.intOrNull ?: element.contentOrNull?.toIntOrNull()
        }
        return null
    }

    private fun parseString(element: JsonElement?): String? {
        if (element is JsonPrimitive) return element.contentOrNull
        return null
    }

    private fun parseStringList(element: JsonElement?): List<String> {
        if (element == null || element !is JsonArray) return emptyList()
        return element.mapNotNull { item ->
            (item as? JsonPrimitive)?.contentOrNull
        }
    }
}
