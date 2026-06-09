package com.movit.core.data.platform

import platform.Foundation.NSUserDefaults

class IosMovitPlatform(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : MovitPlatformBindings {

    override fun apiBaseUrl(): String =
        defaults.stringForKey(KEY_API_BASE_URL)?.takeIf { it.isNotBlank() } ?: baseUrl

    override fun authHeader(): String? {
        val token = defaults.stringForKey(KEY_ACCESS_TOKEN)?.takeIf { it.isNotBlank() } ?: return null
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    override fun preferredLanguage(): String =
        defaults.stringForKey(KEY_LANGUAGE)?.takeIf { it.isNotBlank() } ?: "en"

    override fun userDisplayName(fallback: String): String =
        defaults.stringForKey(KEY_USER_NAME)?.takeIf { it.isNotBlank() } ?: fallback

    override fun readCache(store: String, key: String): String? =
        defaults.stringForKey(cacheKey(store, key))

    override fun writeCache(store: String, key: String, value: String) {
        defaults.setObject(value, cacheKey(store, key))
    }

    override fun removeCache(store: String, key: String) {
        defaults.removeObjectForKey(cacheKey(store, key))
    }

    override fun isProUser(): Boolean = defaults.boolForKey(KEY_IS_PRO)

    override fun activeUserProgramId(): String? =
        defaults.stringForKey(KEY_ACTIVE_USER_PROGRAM_ID)?.takeIf { it.isNotBlank() }

    private fun cacheKey(store: String, key: String): String = "movit_cache_${store}_$key"

    companion object {
        const val DEFAULT_BASE_URL = "https://back.mongz.online/"
        private const val KEY_API_BASE_URL = "movit_api_base_url"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_IS_PRO = "is_pro"
        private const val KEY_ACTIVE_USER_PROGRAM_ID = "active_user_program_id"
    }
}
