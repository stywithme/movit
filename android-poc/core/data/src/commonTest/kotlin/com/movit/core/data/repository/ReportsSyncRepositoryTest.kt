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
}
