package com.movit.core.data.platform

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class AndroidSecureSessionStore(
    context: Context,
    private val legacyPrefsName: String = LegacyAuthTokenKeys.LEGACY_ANDROID_PREFS_NAME,
) : SecureSessionStore {

    private val appContext = context.applicationContext

    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    init {
        migrateFromLegacyIfNeeded()
    }

    override fun saveTokens(tokens: SecureAuthTokens) {
        securePrefs.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .putLong(KEY_EXPIRES_AT, tokens.expiresAtEpochMs)
            .apply()
    }

    override fun readAccessToken(): String? =
        securePrefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }

    override fun readRefreshToken(): String? =
        securePrefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }

    override fun readExpiresAtEpochMs(): Long =
        securePrefs.getLong(KEY_EXPIRES_AT, 0L)

    override fun clearTokens() {
        securePrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT)
            .apply()
        clearLegacyTokenKeys()
    }

    private fun migrateFromLegacyIfNeeded() {
        val legacy = appContext.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        migrateLegacyTokensToSecureStore(
            secure = this,
            readLegacyAccessToken = { legacy.getString(LegacyAuthTokenKeys.ACCESS_TOKEN, null) },
            readLegacyRefreshToken = { legacy.getString(LegacyAuthTokenKeys.REFRESH_TOKEN, null) },
            readLegacyExpiresAt = { legacy.getLong(LegacyAuthTokenKeys.TOKEN_EXPIRES_AT, 0L) },
            clearLegacyTokenKeys = { clearLegacyTokenKeys(legacy) },
        )
    }

    private fun clearLegacyTokenKeys() {
        val legacy = appContext.getSharedPreferences(legacyPrefsName, Context.MODE_PRIVATE)
        clearLegacyTokenKeys(legacy)
    }

    private fun clearLegacyTokenKeys(legacy: android.content.SharedPreferences) {
        legacy.edit()
            .remove(LegacyAuthTokenKeys.ACCESS_TOKEN)
            .remove(LegacyAuthTokenKeys.REFRESH_TOKEN)
            .remove(LegacyAuthTokenKeys.TOKEN_EXPIRES_AT)
            .apply()
    }

    companion object {
        private const val SECURE_PREFS_NAME = "movit_secure_auth"
        private const val KEY_ACCESS_TOKEN = "secure_access_token"
        private const val KEY_REFRESH_TOKEN = "secure_refresh_token"
        private const val KEY_EXPIRES_AT = "secure_token_expires_at"
    }
}
