package com.movit.core.data.repository

import com.movit.core.data.platform.MovitPlatformBindings

class FakeMovitPlatformBindings(
    private val auth: String? = "Bearer test-token",
    private val pro: Boolean = true,
    private val language: String = "en",
    private val userProgramId: String? = "up-1",
) : MovitPlatformBindings {
    private val cache = mutableMapOf<String, String>()

    override fun apiBaseUrl(): String = "https://test.movit.local"

    override fun authHeader(): String? = auth

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
}
