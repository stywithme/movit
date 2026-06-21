package com.movit.core.data.repository

import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.TrainModeDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HomeTrainModeHydratorTest {

    @Test
    fun hydratesTrainModeWhenHomeReportsNoAssessmentButPlanIsActive() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/plan/today") -> respond(
                        content = """
                            {
                              "success": true,
                              "data": {
                                "activePlanStatus": "active",
                                "currentProgram": {
                                  "name": { "en": "Push Pull Leg" },
                                  "weekNumber": 1,
                                  "dayNumber": 2,
                                  "dayType": "training",
                                  "isRestDay": false,
                                  "plannedWorkouts": [
                                    {
                                      "id": "pw-1",
                                      "name": { "en": "Push Day" },
                                      "estimatedDurationMin": 30,
                                      "itemCount": 6,
                                      "isCompleted": false
                                    }
                                  ]
                                },
                                "isTrainingDay": true
                              }
                            }
                        """.trimIndent(),
                        headers = jsonHeaders(),
                    )
                    request.url.encodedPath.endsWith("/plan") -> respond(
                        content = """
                            {
                              "success": true,
                              "data": {
                                "id": "plan-1",
                                "userId": "u1",
                                "status": "active",
                                "programs": [
                                  {
                                    "id": "slot-1",
                                    "sortOrder": 0,
                                    "status": "active",
                                    "program": {
                                      "id": "prog-1",
                                      "name": { "en": "Push Pull Leg" },
                                      "slug": "push-pull-leg",
                                      "type": "training",
                                      "durationWeeks": 4
                                    },
                                    "progress": {
                                      "completedDays": 1,
                                      "totalDays": 7,
                                      "currentWeek": 1,
                                      "currentDay": 2
                                    }
                                  }
                                ],
                                "createdAt": "",
                                "updatedAt": ""
                              }
                            }
                        """.trimIndent(),
                        headers = jsonHeaders(),
                    )
                    else -> respond("{}", headers = jsonHeaders())
                }
            }
            val api = testMobileApi(engine, platform)
            val home = HomeDataDto(
                trainMode = TrainModeDto(status = "no_assessment"),
            )

            val hydrated = HomeTrainModeHydrator.hydrateIfNeeded(
                home = home,
                api = api,
                authorization = "Bearer test-token",
            )

            assertEquals("active", hydrated.trainMode?.status)
            assertEquals("prog-1", hydrated.trainMode?.activeProgram?.id)
            assertNotNull(hydrated.trainMode?.todayWorkout)
            assertEquals("pw-1", hydrated.trainMode?.todayWorkout?.plannedWorkoutId)
        }
    }

    private fun jsonHeaders() = headersOf(
        HttpHeaders.ContentType,
        "application/json",
    )
}
