package com.movit.core.data.repository

import com.movit.core.network.MovitJson
import com.movit.core.network.dto.EffectivePlanApiResponse
import com.movit.core.network.dto.EffectivePlanPayloadDto
import com.movit.core.network.dto.EffectivePlannedWorkoutDto
import com.movit.core.network.dto.ProgramCustomizationKeys
import com.movit.core.network.dto.UserProgramUpdateRequest
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
            val localStore = testLocalStore(platform)
            val engine = MockEngine {
                respond(
                    content = """{"success":true,"data":{"userProgramId":"up-1","weekNumber":1,"dayNumber":1}}""",
                    headers = io.ktor.http.headersOf(
                        io.ktor.http.HttpHeaders.ContentType,
                        "application/json",
                    ),
                )
            }
            val repo = testWorkoutSessionRepository(engine, platform, localStore)

            val result = repo.syncEffectivePlan(userProgramId = "up-1", weekNumber = 1, dayNumber = 1)

            assertTrue(result is AppResult.Success)
            assertNotNull(
                localStore.readString(
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
            val localStore = testLocalStore(platform)
            val cachedResponse = EffectivePlanApiResponse(
                success = true,
                data = EffectivePlanPayloadDto(userProgramId = "up-1"),
            )
            localStore.writeString(
                MovitCacheKeys.SESSION_STORE,
                MovitCacheKeys.effectivePlanKey("up-1", 1, 1),
                MovitJson.encodeToString(EffectivePlanApiResponse.serializer(), cachedResponse),
            )
            val engine = MockEngine { respond("{}", HttpStatusCode.InternalServerError) }
            val repo = testWorkoutSessionRepository(engine, platform, localStore)

            val result = repo.syncEffectivePlan(userProgramId = "up-1", weekNumber = 1, dayNumber = 1)

            assertTrue(result is AppResult.Success)
        }
    }

    @Test
    fun syncEffectivePlan_withoutAuthAndNoCache_fails() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(auth = null)
            val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
            val repo = testWorkoutSessionRepository(engine, platform)

            val result = repo.syncEffectivePlan(userProgramId = "up-1", weekNumber = 1, dayNumber = 1)

            assertTrue(result is AppResult.Failure)
            assertEquals("Sign in to load this workout session.", result.message)
        }
    }

    @Test
    fun saveDayCustomizations_usesDayWeekDayKey_inCache() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
            val baseResponse = EffectivePlanApiResponse(
                success = true,
                data = EffectivePlanPayloadDto(
                    userProgramId = "up-1",
                    weekNumber = 1,
                    dayNumber = 1,
                    plannedWorkouts = listOf(EffectivePlannedWorkoutDto(id = "pw-old")),
                ),
            )
            localStore.writeString(
                MovitCacheKeys.SESSION_STORE,
                MovitCacheKeys.effectivePlanKey("up-1", 1, 1),
                MovitJson.encodeToString(EffectivePlanApiResponse.serializer(), baseResponse),
            )
            val engine = MockEngine { respond("{}", HttpStatusCode.OK) }
            val repo = testWorkoutSessionRepository(engine, platform, localStore)
            val customized = listOf(EffectivePlannedWorkoutDto(id = "pw-new"))
            val dayKey = ProgramCustomizationKeys.dayKey(weekNumber = 1, dayNumber = 1)

            assertEquals("day_1_1", dayKey)

            val result = repo.saveDayCustomizations(
                userProgramId = "up-1",
                weekNumber = 1,
                dayNumber = 1,
                request = UserProgramUpdateRequest(customizations = mapOf(dayKey to customized)),
            )

            assertTrue(result is AppResult.Success)
            val cached = MovitJson.decodeFromString(
                EffectivePlanApiResponse.serializer(),
                localStore.readString(
                    MovitCacheKeys.SESSION_STORE,
                    MovitCacheKeys.effectivePlanKey("up-1", 1, 1),
                )!!,
            )
            assertEquals("pw-new", cached.data?.plannedWorkouts?.singleOrNull()?.id)
        }
    }

    @Test
    fun syncEffectivePlan_networkFailureWithCache_returnsCached() {
        runBlocking {
            val platform = FakeMovitPlatformBindings()
            val localStore = testLocalStore(platform)
            val cachedResponse = EffectivePlanApiResponse(
                success = true,
                data = EffectivePlanPayloadDto(userProgramId = "up-1"),
            )
            localStore.writeString(
                MovitCacheKeys.SESSION_STORE,
                MovitCacheKeys.effectivePlanKey("up-1", 1, 1),
                MovitJson.encodeToString(EffectivePlanApiResponse.serializer(), cachedResponse),
            )
            val engine = MockEngine { respond("down", HttpStatusCode.ServiceUnavailable) }
            val repo = testWorkoutSessionRepository(engine, platform, localStore)

            val result = repo.syncEffectivePlan(userProgramId = "up-1", weekNumber = 1, dayNumber = 1)

            assertTrue(result is AppResult.Success)
        }
    }
}
