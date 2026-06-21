package com.movit.core.data.repository

import com.movit.core.network.MovitJson
import com.movit.core.network.dto.ReportsDashboardApiResponse
import com.movit.shared.AppResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReportsSyncRepositoryTest {

    @Test
    fun syncDashboard_withAuthAndSuccess_writesCache() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(pro = true)
            val localStore = testLocalStore(platform)
            val engine = MockEngine {
                respond(
                    content = """{"success":true}""",
                    headers = io.ktor.http.headersOf(
                        io.ktor.http.HttpHeaders.ContentType,
                        "application/json",
                    ),
                )
            }
            val repo = ReportsSyncRepository(testMobileApi(engine, platform), { platform }, { localStore })

            val result = repo.syncDashboard()

            assertTrue(result is AppResult.Success)
            assertNotNull(localStore.readString(MovitCacheKeys.REPORTS_STORE, MovitCacheKeys.REPORTS_DASHBOARD))
        }
    }

    @Test
    fun syncDashboard_withoutAuth_returnsCachedWhenPresent() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(auth = null, pro = true)
            val localStore = testLocalStore(platform)
            val cached = ReportsDashboardApiResponse(success = true)
            localStore.writeString(
                MovitCacheKeys.REPORTS_STORE,
                MovitCacheKeys.REPORTS_DASHBOARD,
                MovitJson.encodeToString(ReportsDashboardApiResponse.serializer(), cached),
            )
            val engine = MockEngine { respond("{}", HttpStatusCode.InternalServerError) }
            val repo = ReportsSyncRepository(testMobileApi(engine, platform), { platform }, { localStore })

            val result = repo.syncDashboard()

            assertTrue(result is AppResult.Success)
        }
    }

    @Test
    fun syncDashboard_withoutAuthAndNoCache_fails() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(auth = null, pro = true)
            val localStore = testLocalStore(platform)
            val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
            val repo = ReportsSyncRepository(testMobileApi(engine, platform), { platform }, { localStore })

            val result = repo.syncDashboard()

            assertTrue(result is AppResult.Failure)
            assertEquals("Sign in to load your reports.", result.message)
        }
    }

    @Test
    fun syncDashboard_networkFailureWithCache_returnsCached() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(pro = true)
            val localStore = testLocalStore(platform)
            val cached = ReportsDashboardApiResponse(success = true)
            localStore.writeString(
                MovitCacheKeys.REPORTS_STORE,
                MovitCacheKeys.REPORTS_DASHBOARD,
                MovitJson.encodeToString(ReportsDashboardApiResponse.serializer(), cached),
            )
            val engine = MockEngine { respond("down", HttpStatusCode.BadGateway) }
            val repo = ReportsSyncRepository(testMobileApi(engine, platform), { platform }, { localStore })

            val result = repo.syncDashboard()

            assertTrue(result is AppResult.Success)
        }
    }

    @Test
    fun syncDashboard_withoutProButWithCache_returnsCached() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(pro = false)
            val localStore = testLocalStore(platform)
            val cached = ReportsDashboardApiResponse(success = true)
            localStore.writeString(
                MovitCacheKeys.REPORTS_STORE,
                MovitCacheKeys.REPORTS_DASHBOARD,
                MovitJson.encodeToString(ReportsDashboardApiResponse.serializer(), cached),
            )
            val engine = MockEngine { respond("{}", HttpStatusCode.Forbidden) }
            val repo = ReportsSyncRepository(testMobileApi(engine, platform), { platform }, { localStore })

            val result = repo.syncDashboard()

            assertTrue(result is AppResult.Success)
        }
    }

    @Test
    fun syncWeekMetrics_withAuth_writesScopedCacheKey() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(pro = true)
            val localStore = testLocalStore(platform)
            val engine = MockEngine {
                respond(
                    content = """{"success":true,"scope":"week","summary":{"weekNumber":2,"daysTrained":3}}""",
                    headers = io.ktor.http.headersOf(
                        io.ktor.http.HttpHeaders.ContentType,
                        "application/json",
                    ),
                )
            }
            val repo = ReportsSyncRepository(testMobileApi(engine, platform), { platform }, { localStore })

            val result = repo.syncWeekMetrics(programId = "pr-1", weekNumber = 2)

            assertTrue(result is AppResult.Success)
            val cacheKey = MovitCacheKeys.reportsMetricsKey(
                scope = "week",
                programId = "pr-1",
                weekNumber = 2,
            )
            assertNotNull(localStore.readString(MovitCacheKeys.REPORTS_STORE, cacheKey))
        }
    }

    @Test
    fun syncWeekMetrics_withoutAuth_returnsCachedScopedMetrics() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(auth = null, pro = true)
            val localStore = testLocalStore(platform)
            val cacheKey = MovitCacheKeys.reportsMetricsKey(
                scope = "week",
                programId = "pr-1",
                weekNumber = 1,
            )
            val cached = com.movit.core.network.dto.MetricsApiResponse(success = true, scope = "week")
            localStore.writeString(
                MovitCacheKeys.REPORTS_STORE,
                cacheKey,
                MovitJson.encodeToString(com.movit.core.network.dto.MetricsApiResponse.serializer(), cached),
            )
            val engine = MockEngine { respond("{}", HttpStatusCode.InternalServerError) }
            val repo = ReportsSyncRepository(testMobileApi(engine, platform), { platform }, { localStore })

            val result = repo.syncWeekMetrics(programId = "pr-1", weekNumber = 1)

            assertTrue(result is AppResult.Success)
            assertEquals("week", result.value.scope)
        }
    }

    @Test
    fun syncWeekMetrics_withoutProButWithCache_returnsCachedScopedMetrics() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(pro = false)
            val localStore = testLocalStore(platform)
            val cacheKey = MovitCacheKeys.reportsMetricsKey(
                scope = "week",
                programId = "pr-1",
                weekNumber = 1,
            )
            val cached = com.movit.core.network.dto.MetricsApiResponse(success = true, scope = "week")
            localStore.writeString(
                MovitCacheKeys.REPORTS_STORE,
                cacheKey,
                MovitJson.encodeToString(com.movit.core.network.dto.MetricsApiResponse.serializer(), cached),
            )
            val engine = MockEngine { respond("{}", HttpStatusCode.Forbidden) }
            val repo = ReportsSyncRepository(testMobileApi(engine, platform), { platform }, { localStore })

            val result = repo.syncWeekMetrics(programId = "pr-1", weekNumber = 1)

            assertTrue(result is AppResult.Success)
            assertEquals("week", result.value.scope)
        }
    }
}
