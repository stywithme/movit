package com.movit.core.network

/**
 * Read/write surface for bearer tokens used by the Ktor Auth plugin.
 * Implemented in core:data via [MovitPlatformBindings].
 */
interface MovitAuthTokenStore {
    fun readAccessToken(): String?
    fun readRefreshToken(): String?
    fun readExpiresAtEpochMs(): Long
    fun saveTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long)
    fun clearTokens()
}
