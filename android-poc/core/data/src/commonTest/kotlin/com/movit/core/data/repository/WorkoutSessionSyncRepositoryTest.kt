package com.movit.core.data.repository

import com.movit.core.network.MovitJson
import com.movit.core.network.dto.EffectivePlanApiResponse
import com.movit.core.network.dto.EffectivePlanPayloadDto
import com.movit.shared.AppResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkoutSessionSyncRepositoryTest {

    @Test
    fun syncEffectivePlan_withAuthAndSuccess_writesCache() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val engine = MockEngine {
                respond(
                    content = """{"success":true,"data":{"userProgramId":"up-1","weekNumber":1,"dayNumber":1}}""",
                    headers = io.ktor.http.headersOf(
                        io.ktor.http.HttpHeaders.ContentType,
                        "application/json",
                    ),
                )
            }
            val repo = WorkoutSessionSyncRepository(testMobileApi(engine), { platform })

            val result = repo.syncEffectivePlan(userProgramId = "up-1", weekNumber = 1, dayNumber = 1)

            assertTrue(result is AppResult.Success)
            assertNotNull(
                platform.readCache(
                    MovitCacheKeys.SESSION_STORE,
                    MovitCacheKeys.effectivePlanKey("up-1", 1, 1),
                ),
            )
        }
    }

    @Test
    fun syncEffectivePlan_withoutAuth_returnsCachedWhenPresent() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(auth = null)
            val cachedResponse = EffectivePlanApiResponse(
                success = true,
                data = EffectivePlanPayloadDto(userProgramId = "up-1"),
            )
            platform.writeCache(
                MovitCacheKeys.SESSION_STORE,
                MovitCacheKeys.effectivePlanKey("up-1", 1, 1),
                MovitJson.encodeToString(EffectivePlanApiResponse.serializer(), cachedResponse),
            )
            val engine = MockEngine { respond("{}", HttpStatusCode.InternalServerError) }
            val repo = WorkoutSessionSyncRepository(testMobileApi(engine), { platform })

            val result = repo.syncEffectivePlan(userProgramId = "up-1", weekNumber = 1, dayNumber = 1)

            assertTrue(result is AppResult.Success)
        }
    }

    @Test
    fun syncEffectivePlan_withoutAuthAndNoCache_fails() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(auth = null)
            val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
            val repo = WorkoutSessionSyncRepository(testMobileApi(engine), { platform })

            val result = repo.syncEffectivePlan(userProgramId = "up-1", weekNumber = 1, dayNumber = 1)

            assertTrue(result is AppResult.Failure)
            assertEquals("Sign in to load this workout session.", result.message)
        }
    }

    @Test
    fun syncEffectivePlan_networkFailureWithCache_returnsCached() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val cachedResponse = EffectivePlanApiResponse(
                success = true,
                data = EffectivePlanPayloadDto(userProgramId = "up-1"),
            )
            platform.writeCache(
                MovitCacheKeys.SESSION_STORE,
                MovitCacheKeys.effectivePlanKey("up-1", 1, 1),
                MovitJson.encodeToString(EffectivePlanApiResponse.serializer(), cachedResponse),
            )
            val engine = MockEngine { respond("down", HttpStatusCode.ServiceUnavailable) }
            val repo = WorkoutSessionSyncRepository(testMobileApi(engine), { platform })

            val result = repo.syncEffectivePlan(userProgramId = "up-1", weekNumber = 1, dayNumber = 1)

            assertTrue(result is AppResult.Success)
        }
    }
}
