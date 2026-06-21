package com.movit.feature.account

import com.movit.core.network.dto.TrainingProfilePayloadDto
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingProfileSummaryMapperTest {

    @Test
    fun emptyProfile_returnsEmptyKey() {
        val summary = TrainingProfileSummaryMapper.format(TrainingProfilePayloadDto())
        assertEquals(TrainingProfileSummaryMapper.EMPTY_SUMMARY_KEY, summary)
    }

    @Test
    fun populatedProfile_joinsGoalDaysAndLocation() {
        val payload = TrainingProfilePayloadDto(
            trainingGoal = "GENERAL_HEALTH",
            profile = buildJsonObject {
                put("availableDaysPerWeek", 3)
                put("maxSessionMinutes", 45)
                putJsonArray("availableEquipment") {
                    add(JsonPrimitive("dumbbells"))
                }
                put("trainingLocation", "home")
            },
        )

        val summary = TrainingProfileSummaryMapper.format(payload)

        assertTrue(summary.contains("General Health"))
        assertTrue(summary.contains("3 d/w"))
        assertTrue(summary.contains("45 min"))
        assertTrue(summary.contains("Home"))
    }
}
