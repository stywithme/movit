package com.movit.core.data.platform

/**
 * Platform hooks for auth, API base URL, and offline cache.
 * Android delegates to legacy AuthManager (EncryptedSharedPreferences for tokens).
 * iOS uses Keychain for tokens and NSUserDefaults for non-sensitive profile metadata.
 */
interface MovitPlatformBindings {
    fun apiBaseUrl(): String
    fun authHeader(): String?
    fun preferredLanguage(): String
    fun userDisplayName(fallback: String = "Athlete"): String
    fun readCache(store: String, key: String): String?
    fun writeCache(store: String, key: String, value: String)
    fun removeCache(store: String, key: String)

    /** Pro subscription unlocks coach-style reports from the backend. */
    fun isProUser(): Boolean = false

    /** Active enrollment id for effective-plan and customization APIs. */
    fun activeUserProgramId(): String? = null

    /** Persists the active user-program enrollment id (iOS NSUserDefaults; Android via legacy sync). */
    fun setActiveUserProgramId(userProgramId: String?) {}

    /** Exercise thumbnail from legacy local cache (Android) when explore DTO has no image. */
    fun exerciseImageUrl(slug: String): String? = null

    fun userEmail(): String? = null

    fun userAvatarUrl(): String? = null

    fun refreshToken(): String? = null

    fun readAccessTokenRaw(): String? =
        authHeader()?.removePrefix("Bearer ")?.removePrefix("bearer ")?.trim()

    fun tokenExpiresAtEpochMs(): Long = 0L

    fun updateAuthTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long) {}

    fun isOnboardingCompleted(): Boolean = true

    fun totalWorkouts(): Int = 0

    fun totalMinutes(): Int = 0

    fun subscriptionExpiry(): String? = null

    fun voiceFeedbackEnabled(): Boolean = true

    fun notificationsEnabled(): Boolean = true

    fun persistAuthSession(snapshot: AuthSessionSnapshot) {}

    fun clearAuthSession() {}

    fun setOnboardingCompleted(completed: Boolean) {}

    fun updateUserSettings(
        preferredLanguage: String? = null,
        voiceFeedback: Boolean? = null,
        notifications: Boolean? = null,
    ) {}

    /** Persisted appearance mode: `light`, `dark`, or `system`. */
    fun themeMode(): String = MovitThemeModeStorage.SYSTEM

    fun setThemeMode(mode: String) {}

    /** Applies locale immediately (Android: AppCompatDelegate; iOS: UserDefaults). */
    fun applyPreferredLanguage(languageCode: String) {}
}

object MovitThemeModeStorage {
    const val LIGHT = "light"
    const val DARK = "dark"
    const val SYSTEM = "system"
}
