package com.movit.core.data.repository

import com.movit.core.data.platform.AuthSessionSnapshot
import com.movit.core.data.platform.FakeSecureSessionStore
import com.movit.core.data.platform.SecureAuthTokens
import com.movit.shared.AppResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountSyncRepositorySecureSessionTest {

    @Test
    fun logout_clearsPersistedSecureTokens() {
        runBlocking {
            val secure = FakeSecureSessionStore()
            val platform = SecureAwareFakePlatform(secure)
            val engine = MockEngine {
                respond(
                    content = """{"success":true}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
            val repository = AccountSyncRepository(testMobileApi(engine), { platform })

            platform.persistAuthSession(sampleSnapshot())
            assertEquals("access-1", secure.readAccessToken())

            val result = repository.logout()
            assertTrue(result is AppResult.Success)
            assertNull(secure.readAccessToken())
            assertNull(secure.readRefreshToken())
        }
    }

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

    private class SecureAwareFakePlatform(
        private val secure: FakeSecureSessionStore,
    ) : FakeMovitPlatformBindings() {
        override fun authHeader(): String? =
            secure.readAccessToken()?.let { "Bearer $it" }

        override fun refreshToken(): String? = secure.readRefreshToken()

        override fun persistAuthSession(snapshot: AuthSessionSnapshot) {
            secure.saveTokens(
                SecureAuthTokens(
                    accessToken = snapshot.accessToken,
                    refreshToken = snapshot.refreshToken,
                ),
            )
        }

        override fun clearAuthSession() {
            secure.clearTokens()
        }
    }
}
