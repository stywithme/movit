package com.movit.core.data.repository

import com.movit.core.data.platform.AuthSessionSnapshot
import com.movit.core.data.platform.FakeSecureSessionStore
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.platform.MovitThemeModeStorage
import com.movit.core.data.platform.SecureAuthTokens
import com.movit.core.network.MovitClock

open class FakeMovitPlatformBindings(
    private val auth: String? = "Bearer test-token",
    private val pro: Boolean = true,
    private var language: String = "en",
    private var storedThemeMode: String = MovitThemeModeStorage.SYSTEM,
    private var userProgramId: String? = "up-1",
    private val secureSession: FakeSecureSessionStore = FakeSecureSessionStore(),
) : MovitPlatformBindings {
    private val cache = mutableMapOf<String, String>()

    override fun apiBaseUrl(): String = "https://test.movit.local"

    override fun authHeader(): String? =
        secureSession.readAccessToken()?.let { "Bearer $it" } ?: auth

    override fun preferredLanguage(): String = language

    override fun userDisplayName(fallback: String): String = "Test Athlete"

    override fun readCache(store: String, key: String): String? = cache["$store::$key"]

    override fun readAllCacheEntries(store: String): Map<String, String> {
        val prefix = "$store::"
        return cache.filterKeys { it.startsWith(prefix) }
            .mapKeys { (k, _) -> k.removePrefix(prefix) }
    }

    override fun writeCache(store: String, key: String, value: String) {
        cache["$store::$key"] = value
    }

    override fun removeCache(store: String, key: String) {
        cache.remove("$store::$key")
    }

    override fun isProUser(): Boolean = pro

    override fun activeUserProgramId(): String? = userProgramId

    override fun setActiveUserProgramId(userProgramId: String?) {
        this.userProgramId = userProgramId
    }

    override fun refreshToken(): String? = secureSession.readRefreshToken()

    override fun tokenExpiresAtEpochMs(): Long = secureSession.readExpiresAtEpochMs()

    override fun updateAuthTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long) {
        secureSession.saveTokens(
            SecureAuthTokens(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtEpochMs = expiresAtEpochMs,
            ),
        )
    }

    override fun persistAuthSession(snapshot: AuthSessionSnapshot) {
        val expiresAt = if (snapshot.expiresInSeconds > 0) {
            MovitClock.nowEpochMs() + snapshot.expiresInSeconds * 1000L
        } else {
            secureSession.readExpiresAtEpochMs()
        }
        secureSession.saveTokens(
            SecureAuthTokens(
                accessToken = snapshot.accessToken,
                refreshToken = snapshot.refreshToken,
                expiresAtEpochMs = expiresAt,
            ),
        )
    }

    override fun clearAuthSession() {
        secureSession.clearTokens()
    }

    fun secureTokens(): FakeSecureSessionStore = secureSession

    override fun themeMode(): String = storedThemeMode

    override fun setThemeMode(mode: String) {
        storedThemeMode = mode
    }

    override fun applyPreferredLanguage(languageCode: String) {
        language = languageCode
    }

    override fun updateUserSettings(
        preferredLanguage: String?,
        voiceFeedback: Boolean?,
        notifications: Boolean?,
    ) {
        preferredLanguage?.let { language = it }
    }
}
