package com.movit.core.network

import com.movit.core.network.contract.WorkoutExecutionContractFixtures
import com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto
import com.movit.core.network.dto.PlannedWorkoutStartRequestDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MovitMobileApiContractTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun homeDto_parsesTrainingDayFields() = runBlocking {
        val payload = """
            {
              "success": true,
              "data": {
                "trainMode": {
                  "status": "active",
                  "isTrainingDay": false,
                  "catchUpSuggestion": {
                    "missedTrainingDays": 2,
                    "message": "You missed 2 days",
                    "missedSlots": [{"weekNumber": 1, "dayNumber": 3}]
                  },
                  "todayWorkout": {
                    "plannedWorkoutId": "pw-1",
                    "name": {"en": "Leg Day"},
                    "exerciseCount": 5,
                    "workoutTemplateId": "wt-abc",
                    "isCompleted": false,
                    "allWorkoutsCount": 1,
                    "completedWorkoutsCount": 0
                  }
                }
              },
              "timestamp": "2026-06-10T00:00:00Z"
            }
        """.trimIndent()
        val engine = MockEngine {
            respond(payload, HttpStatusCode.OK, jsonHeaders)
        }
        val client = createMovitHttpClientWithEngine(engine = engine, enableLogging = false)
        val api = MovitMobileApi(client) { "https://test.movit.local" }

        val result = api.fetchHome()
        assertTrue(result.isSuccess)
        val trainMode = result.getOrThrow().data?.trainMode
        assertEquals(false, trainMode?.isTrainingDay)
        assertEquals(2, trainMode?.catchUpSuggestion?.missedTrainingDays)
        assertEquals("wt-abc", trainMode?.todayWorkout?.workoutTemplateId)
    }

    @Test
    fun syncDto_parsesFullLegacyPayload() = runBlocking {
        val payload = """
            {
              "success": true,
              "timestamp": "2026-06-10T00:00:00Z",
              "data": {
                "exercises": [{"id": "ex-1", "slug": "squat"}],
                "messageLibrary": [],
                "systemMessages": [{"code": "welcome", "content": {"en": "Hi"}, "updatedAt": "2026-06-10"}],
                "deletedExerciseIds": [],
                "workoutTemplates": [{"id": "wt-1"}],
                "deletedWorkoutTemplateIds": [],
                "programs": [{"id": "pr-1"}],
                "deletedProgramIds": [],
                "userPrograms": [{
                  "id": "up-1",
                  "programId": "pr-1",
                  "startDate": "2026-01-01",
                  "isActive": true,
                  "updatedAt": "2026-06-10",
                  "trainingWeekdays": [1, 3, 5]
                }],
                "userExercisePreferences": [{
                  "exerciseId": "ex-1",
                  "exerciseSlug": "squat",
                  "customReps": 12,
                  "updatedAt": "2026-06-10"
                }],
                "plannedWorkoutReports": [{
                  "id": "rpt-1",
                  "plannedWorkoutId": "pw-1",
                  "programId": "pr-1",
                  "weekNumber": 1,
                  "dayNumber": 1,
                  "startedAt": "2026-06-09T10:00:00Z",
                  "completedAt": "2026-06-09T10:30:00Z",
                  "status": "completed",
                  "totalDurationMs": 1800000,
                  "totalExercises": 3,
                  "totalSets": 9,
                  "completedSets": 9,
                  "totalReps": 72,
                  "avgAccuracy": 95.5
                }],
                "audioManifest": {
                  "baseUrl": "https://cdn.example/audio",
                  "files": [{"filename": "a.mp3", "url": "/a.mp3", "language": "en"}]
                }
              },
              "meta": {
                "totalExercises": 100,
                "totalWorkoutTemplates": 20,
                "totalPrograms": 5,
                "isFullSync": true,
                "serverVersion": "1.0.0",
                "exercisesInResponse": 1,
                "workoutTemplatesInResponse": 1,
                "programsInResponse": 1
              }
            }
        """.trimIndent()
        val engine = MockEngine { respond(payload, HttpStatusCode.OK, jsonHeaders) }
        val client = createMovitHttpClientWithEngine(engine = engine, enableLogging = false)
        val api = MovitMobileApi(client) { "https://test.movit.local" }

        val sync = api.fetchSync().getOrThrow()
        val data = sync.data!!
        assertEquals(1, data.exercises.size)
        assertEquals(1, data.workoutTemplates.size)
        assertEquals(1, data.userPrograms.size)
        assertEquals(listOf(1, 3, 5), data.userPrograms.first().trainingWeekdays)
        assertEquals(1, data.plannedWorkoutReports.size)
        assertEquals("https://cdn.example/audio", data.audioManifest.baseUrl)
        assertEquals(100, sync.meta?.totalExercises)
    }

    @Test
    fun trainingBlockEndpoints_hitExpectedPaths() = runBlocking {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths.add(request.url.encodedPath)
            when {
                request.url.encodedPath.contains("training-config") -> respond(
                    """{"success":true,"data":{"id":"wt-1"}}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
                request.url.encodedPath.contains("audio-manifest") -> respond(
                    """{"success":true,"data":{"entityType":"workout","slug":"leg-day","timestamp":"t","filesInManifest":1,"audioManifest":{"baseUrl":"https://cdn","files":[]}}}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
                request.url.encodedPath.endsWith("/start") -> respond(
                    """{"success":true,"data":{"id":"rpt-1","plannedWorkoutId":"pw-1","weekNumber":1,"dayNumber":1,"status":"in_progress"}}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
                request.url.encodedPath.endsWith("/complete") -> respond(
                    """{"success":true,"data":{"id":"rpt-1","plannedWorkoutId":"pw-1","status":"completed"}}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
                request.url.encodedPath.endsWith("/workout-executions") -> respond(
                    """{"success":true,"data":{"id":"exec-1","exerciseId":"squat"}}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
                request.url.encodedPath.contains("/explore") -> respond(
                    """{"success":true,"data":{"workoutGroupId":"grp-1","savedCount":1,"executions":[{"id":"e1","exerciseId":"squat","totalReps":10}]}}""",
                    HttpStatusCode.OK,
                    jsonHeaders,
                )
                else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
            }
        }
        val client = createMovitHttpClientWithEngine(engine = engine, enableLogging = false)
        val api = MovitMobileApi(client) { "https://test.movit.local" }
        val auth = "Bearer token"

        api.fetchWorkoutTrainingConfig("wt-1", auth).getOrThrow()
        api.fetchWorkoutAudioManifest("leg-day", auth).getOrThrow()
        api.fetchExerciseAudioManifest("squat", auth).getOrThrow()
        api.startPlannedWorkout(
            "pw-1",
            PlannedWorkoutStartRequestDto(programId = "pr-1", weekNumber = 1, dayNumber = 1),
            auth,
        ).getOrThrow()
        api.completePlannedWorkout(
            "pw-1",
            PlannedWorkoutCompleteRequestDto(completedAt = 1_700_000_000_000L, totalReps = 50),
            auth,
        ).getOrThrow()
        val execution = api.uploadWorkoutExecution(
            WorkoutExecutionContractFixtures.sampleExecutionUpload(id = "exec-1", exerciseId = "squat"),
            auth,
        ).getOrThrow()
        val explorePayload = WorkoutExecutionContractFixtures.sampleExploreUploadRequest().copy(
            workoutGroupId = "grp-1",
            workoutTemplateId = "wt-1",
        )
        val explore = api.uploadExploreWorkout(explorePayload, auth).getOrThrow()

        assertTrue(execution.success)
        assertNotNull(explore.data?.workoutGroupId)
        assertEquals(1, explorePayload.executions.size)
        assertNotNull(explorePayload.executions.first().executionMetrics)
        assertTrue(paths.any { it.endsWith("/api/mobile/workout-templates/wt-1/training-config") })
        assertTrue(paths.any { it.endsWith("/api/mobile/workout-templates/leg-day/audio-manifest") })
        assertTrue(paths.any { it.endsWith("/api/mobile/exercises/squat/audio-manifest") })
        assertTrue(paths.any { it.endsWith("/api/mobile/planned-workouts/pw-1/start") })
        assertTrue(paths.any { it.endsWith("/api/mobile/planned-workouts/pw-1/complete") })
        assertTrue(paths.any { it.endsWith("/api/mobile/workout-executions") })
        assertTrue(paths.any { it.endsWith("/api/mobile/workout-executions/explore") })
    }
}
