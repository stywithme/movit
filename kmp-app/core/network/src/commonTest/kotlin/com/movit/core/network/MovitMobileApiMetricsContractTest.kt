package com.movit.core.network

import com.movit.core.network.dto.MetricsQuery
import com.movit.core.network.dto.MetricsScope
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MovitMobileApiMetricsContractTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val successPayload = """{"success":true,"scope":"exercise","summary":{}}"""

    @Test
    fun fetchMetrics_programScope_sendsRequiredQueryParams() = runBlocking {
        val queries = mutableListOf<List<Pair<String, String>>>()
        val engine = metricsEngine(queries)
        val api = testApi(engine)

        api.fetchMetrics(
            MetricsQuery(programId = "pr-1", scope = MetricsScope.Program, includeChildren = true),
            "Bearer token",
        ).getOrThrow()

        assertQueryContains(queries.last(), "programId" to "pr-1", "scope" to "program", "includeChildren" to "true")
    }

    @Test
    fun fetchMetrics_weekScope_sendsWeekNumberAndHistory() = runBlocking {
        val queries = mutableListOf<List<Pair<String, String>>>()
        val engine = metricsEngine(queries)
        val api = testApi(engine)

        api.fetchMetrics(
            MetricsQuery(
                programId = "pr-1",
                scope = MetricsScope.Week,
                weekNumber = 2,
                includeHistory = true,
                includeChildren = true,
            ),
            "Bearer token",
        ).getOrThrow()

        assertQueryContains(
            queries.last(),
            "programId" to "pr-1",
            "scope" to "week",
            "weekNumber" to "2",
            "includeHistory" to "true",
            "includeChildren" to "true",
        )
    }

    @Test
    fun fetchMetrics_dayScope_sendsWeekAndDayNumbers() = runBlocking {
        val queries = mutableListOf<List<Pair<String, String>>>()
        val engine = metricsEngine(queries)
        val api = testApi(engine)

        api.fetchMetrics(
            MetricsQuery(
                programId = "pr-1",
                scope = MetricsScope.Day,
                weekNumber = 1,
                dayNumber = 3,
            ),
            "Bearer token",
        ).getOrThrow()

        assertQueryContains(
            queries.last(),
            "scope" to "day",
            "weekNumber" to "1",
            "dayNumber" to "3",
        )
    }

    @Test
    fun fetchMetrics_plannedWorkoutScope_sendsPlannedWorkoutId() = runBlocking {
        val queries = mutableListOf<List<Pair<String, String>>>()
        val engine = metricsEngine(queries)
        val api = testApi(engine)

        api.fetchMetrics(
            MetricsQuery(
                programId = "pr-1",
                scope = MetricsScope.PlannedWorkout,
                plannedWorkoutId = "pw-42",
                includeChildren = true,
            ),
            "Bearer token",
        ).getOrThrow()

        assertQueryContains(
            queries.last(),
            "scope" to "plannedWorkout",
            "plannedWorkoutId" to "pw-42",
            "includeChildren" to "true",
        )
    }

    @Test
    fun fetchMetrics_exerciseScope_sendsExerciseSlugAndHistory() = runBlocking {
        val queries = mutableListOf<List<Pair<String, String>>>()
        val engine = metricsEngine(queries)
        val api = testApi(engine)

        api.fetchExerciseMetrics("pr-1", "squat", "Bearer token").getOrThrow()

        assertQueryContains(
            queries.last(),
            "scope" to "exercise",
            "exerciseSlug" to "squat",
            "includeHistory" to "true",
        )
    }

    @Test
    fun fetchMetrics_allScopes_hitMetricsPath() = runBlocking {
        val paths = mutableListOf<String>()
        val engine = MockEngine { request ->
            paths += request.url.encodedPath
            respond(successPayload, HttpStatusCode.OK, jsonHeaders)
        }
        val api = testApi(engine)
        val auth = "Bearer token"

        MetricsScope.entries.forEach { scope ->
            api.fetchMetrics(
                MetricsQuery(
                    programId = "pr-1",
                    scope = scope,
                    weekNumber = 1,
                    dayNumber = 1,
                    plannedWorkoutId = "pw-1",
                    exerciseSlug = "squat",
                ),
                auth,
            ).getOrThrow()
        }

        assertEquals(MetricsScope.entries.size, paths.size)
        assertTrue(paths.all { it.endsWith("/api/mobile/reports/metrics") })
    }

    private fun metricsEngine(
        queries: MutableList<List<Pair<String, String>>>,
    ): MockEngine = MockEngine { request ->
        queries += request.url.parameters.entries().map { it.key to it.value.first() }
        respond(successPayload, HttpStatusCode.OK, jsonHeaders)
    }

    private fun testApi(engine: MockEngine): MovitMobileApi {
        val client = createMovitHttpClientWithEngine(engine = engine, enableLogging = false)
        return MovitMobileApi(client) { "https://test.movit.local" }
    }

    private fun assertQueryContains(
        query: List<Pair<String, String>>,
        vararg expected: Pair<String, String>,
    ) {
        expected.forEach { (key, value) ->
            assertEquals(value, query.first { it.first == key }.second, "Missing or wrong query param: $key")
        }
    }
}
