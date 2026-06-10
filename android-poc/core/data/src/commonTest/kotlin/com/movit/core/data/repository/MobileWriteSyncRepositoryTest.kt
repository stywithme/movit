package com.movit.core.data.repository

import com.movit.core.data.cache.MovitCachePolicy
import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.HomeDataDto
import com.movit.core.network.dto.TrainModeDto
import com.movit.core.network.dto.UserExercisePreferenceUpsertRequest
import com.movit.shared.AppResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.util.concurrent.atomic.AtomicInteger

class MobileWriteSyncRepositoryTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val okBody = """{"success":true}"""

    @Test
    fun completePlan_offline_enqueuesWithoutApiCall() {
        runBlocking {
            val apiCalls = AtomicInteger(0)
            val engine = MockEngine {
                apiCalls.incrementAndGet()
                respond(okBody, HttpStatusCode.OK, jsonHeaders)
            }
            val platform = object : FakeMovitPlatformBindings() {
                override fun isNetworkAvailable(): Boolean = false
            }
            val localStore = testLocalStore(platform)
            val repo = testMobileWriteRepository(engine, platform, localStore)

            val result = repo.completePlan("op-complete-offline")

            assertTrue(result is AppResult.Success)
            assertEquals(0, apiCalls.get())
            val outbox = localStore.getOutboxById("op-complete-offline")
            assertEquals(OutboxStatus.PENDING, outbox?.status)
        }
    }

    @Test
    fun upsertExercisePreference_offline_cachesLocallyAndEnqueues() {
        runBlocking {
            val engine = MockEngine { respond(okBody, HttpStatusCode.OK, jsonHeaders) }
            val platform = object : FakeMovitPlatformBindings() {
                override fun isNetworkAvailable(): Boolean = false
            }
            val localStore = testLocalStore(platform)
            val repo = testMobileWriteRepository(engine, platform, localStore)

            val result = repo.upsertExercisePreference(
                exerciseId = "ex-1",
                request = UserExercisePreferenceUpsertRequest(customReps = 12),
                operationId = "op-pref-1",
            )

            assertTrue(result is AppResult.Success)
            assertTrue(
                localStore.readString(
                    MovitCacheKeys.PREFERENCES_STORE,
                    MovitCacheKeys.exercisePreferenceKey("ex-1"),
                )?.contains("12") == true,
            )
            val outbox = localStore.getOutboxById("op-pref-1")
            assertEquals(OutboxStatus.PENDING, outbox?.status)
        }
    }

    @Test
    fun completePlannedWorkout_withoutAuth_fails() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(auth = null)
            val engine = MockEngine { respond(okBody, HttpStatusCode.OK, jsonHeaders) }
            val repo = testMobileWriteRepository(engine, platform)

            val result = repo.completePlannedWorkout(
                workoutId = "pw-1",
                request = com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto(),
            )

            assertTrue(result is AppResult.Failure)
        }
    }
}
