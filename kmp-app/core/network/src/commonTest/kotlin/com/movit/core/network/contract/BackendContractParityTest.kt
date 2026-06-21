package com.movit.core.network.contract

import com.movit.core.network.MovitJson
import com.movit.core.network.dto.LevelDefinitionDto
import com.movit.core.network.dto.PlanMutationResponse
import com.movit.core.network.dto.SubscriptionPlanDto
import com.movit.core.network.dto.TrainingProfilePutRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BackendContractParityTest {

    @Test
    fun subscriptionPlanDto_deserializesMaxWorkoutTemplatesLimit() {
        val json = """
            {
              "id": "plan-1",
              "maxWorkoutTemplatesLimit": 12,
              "maxExercisesLimit": 50
            }
        """.trimIndent()

        val plan = MovitJson.decodeFromString(SubscriptionPlanDto.serializer(), json)
        assertEquals(12, plan.maxWorkoutTemplatesLimit)
        assertEquals(50, plan.maxExercisesLimit)
    }

    @Test
    fun planMutationResponse_deserializesDataAndCompletion() {
        val json = """
            {
              "success": true,
              "data": { "id": "ap-1", "status": "active" },
              "completion": {
                "nextAction": "reassess",
                "nextProgramId": null,
                "reassessmentTemplateId": "tpl-1"
              }
            }
        """.trimIndent()

        val parsed = MovitJson.decodeFromString(PlanMutationResponse.serializer(), json)
        assertTrue(parsed.success)
        assertNotNull(parsed.data)
        assertEquals("ap-1", parsed.data?.id)
        assertEquals("reassess", parsed.completion?.nextAction)
        assertEquals("tpl-1", parsed.completion?.reassessmentTemplateId)
    }

    @Test
    fun trainingProfilePutRequest_serializesBackendFields() {
        val request = TrainingProfilePutRequest(
            currentActivityLevel = "moderate",
            maxWorkoutMinutes = 45,
            knownInjuries = MovitJson.parseToJsonElement("""["knee"]"""),
            trainingGoal = "STRENGTH",
        )
        val json = MovitJson.encodeToString(TrainingProfilePutRequest.serializer(), request)
        val roundTrip = MovitJson.decodeFromString(TrainingProfilePutRequest.serializer(), json)
        assertEquals("moderate", roundTrip.currentActivityLevel)
        assertEquals(45, roundTrip.maxWorkoutMinutes)
        assertNotNull(roundTrip.knownInjuries)
        assertTrue(json.contains("currentActivityLevel"))
        assertTrue(json.contains("maxWorkoutMinutes"))
        assertTrue(json.contains("knownInjuries"))
    }

    @Test
    fun levelDefinitionDto_deserializesIconAndDefaults() {
        val json = """
            {
              "number": 2,
              "code": "L2",
              "name": { "en": "Level 2" },
              "icon": "level-2",
              "entryThreshold": 25,
              "defaults": {
                "setsRange": { "min": 2, "max": 4 },
                "repsRange": { "min": 8, "max": 12 },
                "intensityGuide": "moderate",
                "restBetweenSetsMs": 60000,
                "workoutDurationRange": { "min": 20, "max": 35 },
                "weeklyFrequencyRange": { "min": 3, "max": 4 }
              }
            }
        """.trimIndent()

        val level = MovitJson.decodeFromString(LevelDefinitionDto.serializer(), json)
        assertEquals("level-2", level.icon)
        assertEquals(2, level.defaults?.setsRange?.min)
        assertEquals(12, level.defaults?.repsRange?.max)
    }
}
