package com.movit.core.data.repository

import com.movit.core.data.platform.AuthSessionSnapshot
import com.movit.core.data.platform.FakeSecureSessionStore
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.platform.MovitThemeModeStorage
import com.movit.core.data.platform.SecureAuthTokens

open class FakeMovitPlatformBindings(
    private val auth: String? = "Bearer test-token",
    private val pro: Boolean = true,
    private var language: String = "en",
    private var storedThemeMode: String = MovitThemeModeStorage.SYSTEM,
    private val userProgramId: String? = "up-1",
    private val secureSession: FakeSecureSessionStore = FakeSecureSessionStore(),
) : MovitPlatformBindings {
    private val cache = mutableMapOf<String, String>()

    override fun apiBaseUrl(): String = "https://test.movit.local"

    override fun authHeader(): String? =
        secureSession.readAccessToken()?.let { "Bearer $it" } ?: auth

    override fun preferredLanguage(): String = language

    override fun userDisplayName(fallback: String): String = "Test Athlete"

    override fun readCache(store: String, key: String): String? = cache["$store::$key"]

    override fun writeCache(store: String, key: String, value: String) {
        cache["$store::$key"] = value
    }

    override fun removeCache(store: String, key: String) {
        cache.remove("$store::$key")
    }

    override fun isProUser(): Boolean = pro

    override fun activeUserProgramId(): String? = userProgramId

    override fun refreshToken(): String? = secureSession.readRefreshToken()

    override fun persistAuthSession(snapshot: AuthSessionSnapshot) {
        secureSession.saveTokens(
            SecureAuthTokens(
                accessToken = snapshot.accessToken,
                refreshToken = snapshot.refreshToken,
                expiresAtEpochMs = snapshot.expiresInSeconds * 1000L,
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
