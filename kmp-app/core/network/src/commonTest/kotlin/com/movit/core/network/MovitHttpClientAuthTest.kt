package com.movit.core.network

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovitHttpClientAuthTest {

  private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
  private val baseNow = 1_700_000_000_000L

  @AfterTest
  fun resetClock() {
    MovitClock.resetToPlatformClock()
  }

  @Test
  fun unauthorized_thenRefresh_thenRetrySucceeds() = runBlocking {
    MovitClock.nowEpochMs = { baseNow }
    val store = FakeMovitAuthTokenStore(
      accessToken = "stale-access",
      expiresAtEpochMs = baseNow + 86_400_000L,
    )
    var homeCalls = 0
    val engine = MockEngine { request ->
      when {
        request.url.encodedPath.endsWith("/auth/refresh") -> respond(
          content = """{"success":true,"data":{"accessToken":"fresh-access","refreshToken":"refresh-token","expiresIn":86400}}""",
          status = HttpStatusCode.OK,
          headers = jsonHeaders,
        )
        request.url.encodedPath.endsWith("/api/mobile/home") -> {
          homeCalls++
          if (homeCalls == 1) {
            respond("""{"error":"unauthorized"}""", HttpStatusCode.Unauthorized, jsonHeaders)
          } else {
            respond("""{"success":true,"data":{},"timestamp":"2026-06-10"}""", HttpStatusCode.OK, jsonHeaders)
          }
        }
        else -> respond("{}", HttpStatusCode.NotFound, jsonHeaders)
      }
    }
    var sessionExpired = false
    val refreshClient = createMovitHttpClientWithEngine(engine = engine)
    val client = createMovitHttpClientWithEngine(
      engine = engine,
      enableLogging = false,
      auth = MovitHttpClientConfig(
        tokenStore = store,
        baseUrlProvider = { "https://test.movit.local" },
        refreshHttpClient = refreshClient,
        onSessionExpired = { sessionExpired = true },
      ),
    )

    val api = MovitMobileApi(client) { "https://test.movit.local" }
    val result = api.fetchHome()

    assertTrue(result.isSuccess)
    assertEquals("fresh-access", store.access)
    assertEquals(2, homeCalls)
    assertFalse(sessionExpired)
    assertFalse(store.cleared)
  }

  @Test
  fun failedRefresh_clearsSessionAndNotifies() = runBlocking {
    MovitClock.nowEpochMs = { baseNow }
    val store = FakeMovitAuthTokenStore()
    val engine = MockEngine { request ->
      when {
        request.url.encodedPath.endsWith("/auth/refresh") -> respond(
          content = """{"success":false,"error":"invalid refresh"}""",
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
    var sessionExpired = false
    val refreshClient = createMovitHttpClientWithEngine(engine = engine)
    val client = createMovitHttpClientWithEngine(
      engine = engine,
      enableLogging = false,
      auth = MovitHttpClientConfig(
        tokenStore = store,
        baseUrlProvider = { "https://test.movit.local" },
        refreshHttpClient = refreshClient,
        onSessionExpired = { sessionExpired = true },
      ),
    )

    val api = MovitMobileApi(client) { "https://test.movit.local" }
    val result = api.fetchHome()

    assertTrue(result.isFailure)
    assertTrue(sessionExpired)
    assertTrue(store.cleared)
  }

  @Test
  fun refreshServerErrorDoesNotExpireSession() = runBlocking {
    MovitClock.nowEpochMs = { baseNow }
    val store = FakeMovitAuthTokenStore(
      accessToken = "stale-access",
      expiresAtEpochMs = baseNow + 86_400_000L,
    )
    val engine = MockEngine { request ->
      when {
        request.url.encodedPath.endsWith("/auth/refresh") -> respond(
          content = """{"error":"unavailable"}""",
          status = HttpStatusCode.ServiceUnavailable,
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
    var sessionExpired = false
    val refreshClient = createMovitHttpClientWithEngine(engine = engine)
    val client = createMovitHttpClientWithEngine(
      engine = engine,
      enableLogging = false,
      auth = MovitHttpClientConfig(
        tokenStore = store,
        baseUrlProvider = { "https://test.movit.local" },
        refreshHttpClient = refreshClient,
        onSessionExpired = { sessionExpired = true },
      ),
    )

    val api = MovitMobileApi(client) { "https://test.movit.local" }
    val result = api.fetchHome()

    assertTrue(result.isFailure)
    assertFalse(sessionExpired)
    assertFalse(store.cleared)
    assertEquals("stale-access", store.access)
  }

  @Test
  fun locallyExpiredToken_refreshesBeforeCall() = runBlocking {
    MovitClock.nowEpochMs = { baseNow }
    val store = FakeMovitAuthTokenStore(
      accessToken = "stale-access",
      expiresAtEpochMs = baseNow - 1_000L,
    )
    var homeCalls = 0
    var refreshCalls = 0
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
    val refreshClient = createMovitHttpClientWithEngine(engine = engine)
    val client = createMovitHttpClientWithEngine(
      engine = engine,
      enableLogging = false,
      auth = MovitHttpClientConfig(
        tokenStore = store,
        baseUrlProvider = { "https://test.movit.local" },
        refreshHttpClient = refreshClient,
        onSessionExpired = {},
      ),
    )

    val api = MovitMobileApi(client) { "https://test.movit.local" }
    val result = api.fetchHome()

    assertTrue(result.isSuccess)
    assertTrue(refreshCalls >= 1)
    assertEquals(1, homeCalls)
    assertEquals("fresh-access", store.access)
  }
}
