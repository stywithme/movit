package com.movit.core.data.repository

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

class PlanSyncRepositoryTest {

    @Test
    fun enrollProgram_persistsActiveUserProgramId() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(userProgramId = null)
            val engine = MockEngine { request ->
                when {
                    request.url.encodedPath.endsWith("/plan/enroll") -> respond(
                        content = """{"success":true,"data":{"id":"plan-1","userId":"u1","status":"active","programs":[],"createdAt":"","updatedAt":""}}""",
                        headers = jsonHeaders(),
                    )
                    request.url.encodedPath.endsWith("/sync") -> respond(
                        content = """{"success":true,"data":{"userPrograms":[{"id":"up-enrolled","programId":"prog-1","isActive":true}]}}""",
                        headers = jsonHeaders(),
                    )
                    request.url.encodedPath.endsWith("/home") -> respond(
                        content = """{"success":true,"data":{},"timestamp":"2026-06-10T00:00:00Z"}""",
                        headers = jsonHeaders(),
                    )
                    else -> respond("", HttpStatusCode.NotFound)
                }
            }
            val api = testMobileApi(engine, platform)
            val repo = PlanSyncRepository(
                api = api,
                platform = { platform },
                homeSync = HomeSyncRepository(api, { platform }),
            )

            val result = repo.enrollProgram("prog-1")

            assertTrue(result is AppResult.Success)
            assertEquals("up-enrolled", result.value)
            assertEquals("up-enrolled", platform.activeUserProgramId())
        }
    }

    @Test
    fun refreshActiveUserProgramId_hydratesFromSync() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(userProgramId = null)
            val engine = MockEngine {
                respond(
                    content = """{"success":true,"data":{"userPrograms":[{"id":"up-active","programId":"prog-9","isActive":true}]}}""",
                    headers = jsonHeaders(),
                )
            }
            val api = testMobileApi(engine, platform)
            val repo = PlanSyncRepository(
                api = api,
                platform = { platform },
                homeSync = HomeSyncRepository(api, { platform }),
            )

            val result = repo.refreshActiveUserProgramId()

            assertTrue(result is AppResult.Success)
            assertEquals("up-active", result.value)
            assertEquals("up-active", platform.activeUserProgramId())
        }
    }

    private fun jsonHeaders() = headersOf(
        HttpHeaders.ContentType,
        "application/json",
    )
}
