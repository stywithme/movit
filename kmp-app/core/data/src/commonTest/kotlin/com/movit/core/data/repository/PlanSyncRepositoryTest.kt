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

private fun planSyncRepository(
    api: com.movit.core.network.MovitMobileApi,
    platform: FakeMovitPlatformBindings,
    localStore: com.movit.core.data.local.MovitLocalStore,
): PlanSyncRepository {
    val home = HomeSyncRepository(api, { platform }, { localStore })
    return PlanSyncRepository(
        api = api,
        platform = { platform },
        homeSync = home,
        userProgramEnrollments = testUserProgramEnrollmentStore(localStore),
    )
}

class PlanSyncRepositoryTest {

    @Test
    fun enrollProgram_persistsActiveUserProgramId() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(userProgramId = null)
            val requestOrder = mutableListOf<String>()
            val engine = MockEngine { request ->
                requestOrder += request.url.encodedPath.substringAfterLast("/")
                when {
                    request.url.encodedPath.endsWith("/plan/enroll") -> respond(
                        content = """{"success":true,"data":{"id":"plan-1","userId":"u1","status":"active","programs":[],"createdAt":"","updatedAt":""}}""",
                        headers = jsonHeaders(),
                    )
                    request.url.encodedPath.endsWith("/user-programs") -> respond(
                        content = """{"success":true,"userPrograms":[{"id":"up-enrolled","programId":"prog-1","isActive":true}]}""",
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
            val localStore = testLocalStore(platform)
            val repo = planSyncRepository(api, platform, localStore)

            val result = repo.enrollProgram("prog-1")

            assertTrue(result is AppResult.Success)
            assertEquals("up-enrolled", result.value)
            assertEquals("up-enrolled", platform.activeUserProgramId())
            assertEquals(listOf("enroll", "user-programs", "home"), requestOrder)
        }
    }

    @Test
    fun refreshActiveUserProgramId_hydratesFromSync() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(userProgramId = null)
            val engine = MockEngine { request ->
                assertTrue(request.url.encodedPath.endsWith("/user-programs"))
                assertEquals(null, request.url.parameters["updatedAfter"])
                respond(
                    content = """{"success":true,"userPrograms":[{"id":"up-active","programId":"prog-9","isActive":true}]}""",
                    headers = jsonHeaders(),
                )
            }
            val api = testMobileApi(engine, platform)
            val localStore = testLocalStore(platform)
            val repo = planSyncRepository(api, platform, localStore)

            val result = repo.refreshActiveUserProgramId()

            assertTrue(result is AppResult.Success)
            assertEquals("up-active", result.value)
            assertEquals("up-active", platform.activeUserProgramId())
        }
    }

    @Test
    fun refreshActiveUserProgramId_prefersRequestedProgram() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(userProgramId = null)
            val engine = MockEngine { request ->
                assertTrue(request.url.encodedPath.endsWith("/user-programs"))
                assertEquals(null, request.url.parameters["updatedAfter"])
                respond(
                    content = """
                        {
                          "success": true,
                          "userPrograms": [
                              {"id":"up-other","programId":"prog-other","isActive":true},
                              {"id":"up-target","programId":"prog-target","isActive":true}
                            ]
                        }
                    """.trimIndent(),
                    headers = jsonHeaders(),
                )
            }
            val api = testMobileApi(engine, platform)
            val localStore = testLocalStore(platform)
            val repo = planSyncRepository(api, platform, localStore)

            val result = repo.refreshActiveUserProgramId(programId = "prog-target")

            assertTrue(result is AppResult.Success)
            assertEquals("up-target", result.value)
            assertEquals("up-target", platform.activeUserProgramId())
        }
    }

    @Test
    fun refreshActiveUserProgramId_usesLocalEnrollmentWithoutExtraSync() {
        runBlocking {
            val platform = FakeMovitPlatformBindings(userProgramId = null)
            val engine = MockEngine { respond("", HttpStatusCode.NotFound) }
            val api = testMobileApi(engine, platform)
            val localStore = testLocalStore(platform)
            val enrollments = testUserProgramEnrollmentStore(localStore)
            enrollments.hydrateFromSync(
                rows = listOf(
                    com.movit.core.network.dto.UserProgramExportDto(
                        id = "up-cached",
                        programId = "prog-1",
                        isActive = true,
                        trainingWeekdays = listOf(2, 4),
                    ),
                ),
                isFullSync = true,
            )
            val repo = planSyncRepository(api, platform, localStore)

            val result = repo.refreshActiveUserProgramId()

            assertTrue(result is AppResult.Success)
            assertEquals("up-cached", result.value)
            assertEquals("up-cached", platform.activeUserProgramId())
        }
    }

    private fun jsonHeaders() = headersOf(
        HttpHeaders.ContentType,
        "application/json",
    )
}
