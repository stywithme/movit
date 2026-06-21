package com.movit.core.network

class FakeMovitAuthTokenStore(
    accessToken: String = "old-access",
    refreshToken: String = "refresh-token",
    expiresAtEpochMs: Long = movitNowEpochMs() + 86_400_000L,
) : MovitAuthTokenStore {
    var access: String? = accessToken
    var refresh: String? = refreshToken
    var expiresAt: Long = expiresAtEpochMs
    var cleared: Boolean = false

    override fun readAccessToken(): String? = access
    override fun readRefreshToken(): String? = refresh
    override fun readExpiresAtEpochMs(): Long = expiresAt
    override fun saveTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long) {
        access = accessToken
        refresh = refreshToken
        expiresAt = expiresAtEpochMs
        cleared = false
    }
    override fun clearTokens() {
        access = null
        refresh = null
        expiresAt = 0L
        cleared = true
    }
}
