package com.movit.core.data.platform

/**
 * Platform hooks for auth, API base URL, and offline cache.
 * Android delegates to legacy AuthManager / ApiConfig / SharedPreferences.
 * iOS uses NSUserDefaults with the same logical keys where possible.
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
}
