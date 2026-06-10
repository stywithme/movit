package com.movit.core.data.platform

import com.movit.core.network.MovitAuthTokenStore

class PlatformMovitAuthTokenStore(
    private val platform: () -> MovitPlatformBindings,
) : MovitAuthTokenStore {
    override fun readAccessToken(): String? =
        platform().readAccessTokenRaw()

    override fun readRefreshToken(): String? =
        platform().refreshToken()

    override fun readExpiresAtEpochMs(): Long =
        platform().tokenExpiresAtEpochMs()

    override fun saveTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long) {
        platform().updateAuthTokens(accessToken, refreshToken, expiresAtEpochMs)
    }

    override fun clearTokens() {
        platform().clearAuthSession()
    }
}
