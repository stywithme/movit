package com.movit.core.data.platform

class FakeSecureSessionStore : SecureSessionStore {
    private var tokens: SecureAuthTokens? = null

    override fun saveTokens(tokens: SecureAuthTokens) {
        this.tokens = tokens
    }

    override fun readAccessToken(): String? = tokens?.accessToken

    override fun readRefreshToken(): String? = tokens?.refreshToken

    override fun readExpiresAtEpochMs(): Long = tokens?.expiresAtEpochMs ?: 0L

    override fun clearTokens() {
        tokens = null
    }
}
