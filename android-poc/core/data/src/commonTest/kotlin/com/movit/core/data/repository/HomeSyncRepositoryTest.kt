package com.movit.core.data.repository

import com.movit.core.network.MovitJson
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.HomeDataDto
import com.movit.shared.AppResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HomeSyncRepositoryTest {

    @Test
    fun sync_withAuthAndSuccess_writesCacheAndReturnsData() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val engine = MockEngine {
                respond(
                    content = """{"success":true,"data":{},"timestamp":"2026-06-09"}""",
                    headers = io.ktor.http.headersOf(
                        io.ktor.http.HttpHeaders.ContentType,
                        "application/json",
                    ),
                )
            }
            val repo = HomeSyncRepository(testMobileApi(engine, platform), { platform })

            val result = repo.sync()

            assertTrue(result is AppResult.Success)
            assertNotNull(platform.readCache(MovitCacheKeys.HOME_STORE, MovitCacheKeys.HOME_DATA))
        }
    }

    @Test
    fun sync_withoutAuth_returnsCachedWhenPresent() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(auth = null)
            val cached = HomeDataDto()
            platform.writeCache(
                MovitCacheKeys.HOME_STORE,
                MovitCacheKeys.HOME_DATA,
                MovitJson.encodeToString(HomeDataDto.serializer(), cached),
            )
            val engine = MockEngine { respond("{}", HttpStatusCode.InternalServerError) }
            val repo = HomeSyncRepository(testMobileApi(engine, platform), { platform })

            val result = repo.sync()

            assertTrue(result is AppResult.Success)
        }
    }

    @Test
    fun sync_withoutAuthAndNoCache_fails() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(auth = null)
            val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
            val repo = HomeSyncRepository(testMobileApi(engine, platform), { platform })

            val result = repo.sync()

            assertTrue(result is AppResult.Failure)
            assertEquals("Sign in to load your home dashboard.", result.message)
        }
    }

    @Test
    fun sync_networkFailureWithCache_returnsCached() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val cached = HomeDataDto()
            platform.writeCache(
                MovitCacheKeys.HOME_STORE,
                MovitCacheKeys.HOME_DATA,
                MovitJson.encodeToString(HomeDataDto.serializer(), cached),
            )
            val engine = MockEngine { respond("Server error", HttpStatusCode.InternalServerError) }
            val repo = HomeSyncRepository(testMobileApi(engine, platform), { platform })

            val result = repo.sync()

            assertTrue(result is AppResult.Success)
        }
    }
}
