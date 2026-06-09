package com.movit.core.data.platform

/**
 * Platform-agnostic contract for storing auth tokens outside plain preferences.
 * Android uses EncryptedSharedPreferences; iOS uses Keychain.
 */
data class SecureAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long = 0L,
)

interface SecureSessionStore {
    fun saveTokens(tokens: SecureAuthTokens)
    fun readAccessToken(): String?
    fun readRefreshToken(): String?
    fun readExpiresAtEpochMs(): Long
    fun clearTokens()
}

/** Shared logical keys used by legacy plain storage (migration only). */
object LegacyAuthTokenKeys {
    const val ACCESS_TOKEN = "access_token"
    const val REFRESH_TOKEN = "refresh_token"
    const val TOKEN_EXPIRES_AT = "token_expires_at"
    const val LEGACY_ANDROID_PREFS_NAME = "app_prefs"
}

/**
 * One-time migration: copy tokens from legacy plain storage into [secure], then wipe legacy keys.
 */
fun migrateLegacyTokensToSecureStore(
    secure: SecureSessionStore,
    readLegacyAccessToken: () -> String?,
    readLegacyRefreshToken: () -> String?,
    readLegacyExpiresAt: () -> Long,
    clearLegacyTokenKeys: () -> Unit,
) {
    if (!secure.readAccessToken().isNullOrBlank()) return
    val access = readLegacyAccessToken()?.takeIf { it.isNotBlank() } ?: return
    val refresh = readLegacyRefreshToken().orEmpty()
    val expiresAt = readLegacyExpiresAt()
    secure.saveTokens(
        SecureAuthTokens(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochMs = expiresAt,
        ),
    )
    clearLegacyTokenKeys()
}
