package com.movit.core.data.repository

import com.movit.core.data.MovitData
import com.movit.core.data.platform.PlatformMovitAuthTokenStore
import com.movit.core.data.platform.SecureAuthTokens
import com.movit.core.network.MovitClock
import com.movit.core.network.MovitHttpClientConfig
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.createMovitHttpClientWithEngine
import com.movit.shared.AppResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TokenLifecycleIntegrationTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val baseNow = 1_700_000_000_000L

    @AfterTest
    fun resetClock() {
        MovitClock.nowEpochMs = { System.currentTimeMillis() }
        MovitData.onSessionExpired = null
    }

    @Test
    fun homeSync_unauthorizedThenRefresh_retriesSuccessfully() = runBlocking {
        MovitClock.nowEpochMs = { baseNow }
        val platform = FakeMovitPlatformBindings()
        platform.secureTokens().saveTokens(
            SecureAuthTokens(
                accessToken = "stale-access",
                refreshToken = "refresh-token",
                expiresAtEpochMs = baseNow + 86_400_000L,
            ),
        )
        var homeCalls = 0
        val engine = mockHomeEngine(
            onHome = {
                homeCalls++
                homeCalls == 1
            },
        )
        val repo = homeRepo(platform, engine)

        val result = repo.sync()

        assertTrue(result is AppResult.Success)
        assertEquals(2, homeCalls)
        assertEquals("fresh-access", platform.secureTokens().readAccessToken())
    }

    @Test
    fun homeSync_failedRefresh_clearsSessionAndNotifies() = runBlocking {
        MovitClock.nowEpochMs = { baseNow }
        val platform = FakeMovitPlatformBindings()
        platform.secureTokens().saveTokens(
            SecureAuthTokens(
                accessToken = "stale-access",
                refreshToken = "refresh-token",
                expiresAtEpochMs = baseNow + 86_400_000L,
            ),
        )
        var expired = false
        MovitData.onSessionExpired = { expired = true }
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/auth/refresh") -> respond(
                    content = """{"success":false,"error":"invalid"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
                request.url.encodedPath.endsWith("/api/mobile/home") -> respond(
                    content = """{"error":"unauthorized"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders,
                )
                else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
            }
        }
        val repo = homeRepo(platform, engine, onSessionExpired = { MovitData.notifySessionExpired() })

        val result = repo.sync()

        assertTrue(result is AppResult.Failure)
        assertTrue(expired)
        assertNull(platform.secureTokens().readAccessToken())
    }

    @Test
    fun homeSync_locallyExpiredToken_refreshesBeforeCall() = runBlocking {
        MovitClock.nowEpochMs = { baseNow }
        val platform = FakeMovitPlatformBindings()
        platform.secureTokens().saveTokens(
            SecureAuthTokens(
                accessToken = "stale-access",
                refreshToken = "refresh-token",
                expiresAtEpochMs = baseNow - 1_000L,
            ),
        )
        var refreshCalls = 0
        var homeCalls = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/auth/refresh") -> {
                    refreshCalls++
                    respond(
                        content = """{"success":true,"data":{"accessToken":"fresh-access","refreshToken":"refresh-token","expiresIn":86400}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }
                request.url.encodedPath.endsWith("/api/mobile/home") -> {
                    homeCalls++
                    val auth = request.headers[HttpHeaders.Authorization]
                    if (auth == "Bearer fresh-access") {
                        respond("""{"success":true,"data":{},"timestamp":"2026-06-10"}""", HttpStatusCode.OK, jsonHeaders)
                    } else {
                        respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                    }
                }
                else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
            }
        }
        val repo = homeRepo(platform, engine)

        val result = repo.sync()

        assertTrue(result is AppResult.Success)
        assertEquals(1, refreshCalls)
        assertEquals(1, homeCalls)
        assertEquals("fresh-access", platform.secureTokens().readAccessToken())
    }

    private fun mockHomeEngine(onHome: () -> Boolean): MockEngine = MockEngine { request ->
        when {
            request.url.encodedPath.endsWith("/auth/refresh") -> respond(
                content = """{"success":true,"data":{"accessToken":"fresh-access","refreshToken":"refresh-token","expiresIn":86400}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders,
            )
            request.url.encodedPath.endsWith("/api/mobile/home") -> {
                if (onHome()) {
                    respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                } else {
                    respond("""{"success":true,"data":{},"timestamp":"2026-06-10"}""", HttpStatusCode.OK, jsonHeaders)
                }
            }
            else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
        }
    }

    private fun homeRepo(
        platform: FakeMovitPlatformBindings,
        engine: MockEngine,
        onSessionExpired: () -> Unit = {},
    ): HomeSyncRepository {
        val tokenStore = PlatformMovitAuthTokenStore { platform }
        val refreshClient = createMovitHttpClientWithEngine(engine = engine)
        val client = createMovitHttpClientWithEngine(
            engine = engine,
            auth = MovitHttpClientConfig(
                tokenStore = tokenStore,
                baseUrlProvider = { "https://test.movit.local" },
                refreshHttpClient = refreshClient,
                onSessionExpired = onSessionExpired,
            ),
        )
        return HomeSyncRepository(
            api = MovitMobileApi(client) { "https://test.movit.local" },
            platform = { platform },
        )
    }
}
