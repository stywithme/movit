package com.movit.core.data.repository

import com.movit.core.data.outbox.OutboxStatus
import com.movit.core.data.platform.AuthSessionSnapshot
import com.movit.core.network.dto.ExecutionMetricsDto
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto
import com.movit.shared.AppResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LogoutFlushesOrWarnsOutboxTest {

    @Test
    fun prepareLogout_withPendingRowsAfterOfflineFlush_warns() = runBlocking {
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = false
        }
        val localStore = testLocalStore(platform)
        val engine = MockEngine { respond("""{"success":true}""", HttpStatusCode.OK, jsonHeaders) }
        val offlineWrites = testOfflineWriteQueue(testMobileApi(engine, platform), platform, localStore)
        val mobileWrites = testMobileWriteRepository(engine, platform, localStore)
        val repository = AccountSyncRepository(
            api = testMobileApi(engine, platform),
            platform = { platform },
            offlineWrites = { offlineWrites },
        )

        platform.persistAuthSession(sampleSnapshot())
        mobileWrites.uploadWorkoutExecution(sampleExecutionRequest("exec-logout-1"))

        val prep = repository.prepareLogout()
        assertTrue(prep.flushAttempted)
        assertEquals(1L, prep.pendingCount)
        assertTrue(prep.requiresWarning)
    }

    @Test
    fun logout_withoutDiscard_blockedWhenOutboxPending() = runBlocking {
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = false
        }
        val localStore = testLocalStore(platform)
        val engine = MockEngine { respond("""{"success":true}""", HttpStatusCode.OK, jsonHeaders) }
        val offlineWrites = testOfflineWriteQueue(testMobileApi(engine, platform), platform, localStore)
        val mobileWrites = testMobileWriteRepository(engine, platform, localStore)
        val repository = AccountSyncRepository(
            api = testMobileApi(engine, platform),
            platform = { platform },
            offlineWrites = { offlineWrites },
        )

        platform.persistAuthSession(sampleSnapshot())
        mobileWrites.uploadWorkoutExecution(sampleExecutionRequest("exec-logout-2"))

        val result = repository.logout()
        assertTrue(result is AppResult.Failure)
        assertEquals(1L, localStore.countOutboxByStatus(OutboxStatus.PENDING))
        assertTrue(platform.authHeader() != null)
    }

    @Test
    fun logout_withDiscard_clearsSessionDespitePendingOutbox() = runBlocking {
        val platform = object : FakeMovitPlatformBindings() {
            override fun isNetworkAvailable(): Boolean = false
        }
        val localStore = testLocalStore(platform)
        val engine = MockEngine { respond("""{"success":true}""", HttpStatusCode.OK, jsonHeaders) }
        val offlineWrites = testOfflineWriteQueue(testMobileApi(engine, platform), platform, localStore)
        val mobileWrites = testMobileWriteRepository(engine, platform, localStore)
        val repository = AccountSyncRepository(
            api = testMobileApi(engine, platform),
            platform = { platform },
            offlineWrites = { offlineWrites },
        )

        platform.persistAuthSession(sampleSnapshot())
        mobileWrites.uploadWorkoutExecution(sampleExecutionRequest("exec-logout-3"))

        val result = repository.logout(discardPendingOutbox = true)
        assertTrue(result is AppResult.Success)
        assertNull(platform.authHeader())
        // MovitData not installed in unit test — outbox rows remain; discard only bypasses the block.
        assertEquals(1L, localStore.countOutboxByStatus(OutboxStatus.PENDING))
    }

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private fun sampleExecutionRequest(id: String) = WorkoutExecutionUploadRequestDto(
        id = id,
        exerciseId = "squat",
        timestamp = 1L,
        durationMs = 1000,
        totalReps = 5,
        countedReps = 5,
        invalidReps = 0,
        executionMetrics = ExecutionMetricsDto(
            avgRom = 9f,
            avgStability = 8f,
            avgFormScore = 8f,
            avgAlignmentAccuracy = 8f,
            totalTUT = 1000,
        ),
    )

    private fun sampleSnapshot() = AuthSessionSnapshot(
        accessToken = "access-1",
        refreshToken = "refresh-1",
        expiresInSeconds = 3600,
        userId = "u-1",
        name = "Athlete",
        email = "a@test.com",
        avatarUrl = null,
        preferredLanguage = "en",
        voiceFeedback = true,
        notifications = true,
        isPro = false,
        subscriptionExpiry = null,
        totalWorkouts = 0,
        totalMinutes = 0,
    )
}
