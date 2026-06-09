package com.movit.core.data.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecureSessionMigrationTest {

    @Test
    fun migrateLegacyTokensToSecureStore_copiesAndClearsLegacy() {
        val secure = FakeSecureSessionStore()
        var legacyCleared = false
        val legacy = mutableMapOf(
            LegacyAuthTokenKeys.ACCESS_TOKEN to "legacy-access",
            LegacyAuthTokenKeys.REFRESH_TOKEN to "legacy-refresh",
        )

        migrateLegacyTokensToSecureStore(
            secure = secure,
            readLegacyAccessToken = { legacy[LegacyAuthTokenKeys.ACCESS_TOKEN] },
            readLegacyRefreshToken = { legacy[LegacyAuthTokenKeys.REFRESH_TOKEN] },
            readLegacyExpiresAt = { 42L },
            clearLegacyTokenKeys = {
                legacyCleared = true
                legacy.clear()
            },
        )

        assertEquals("legacy-access", secure.readAccessToken())
        assertEquals("legacy-refresh", secure.readRefreshToken())
        assertEquals(42L, secure.readExpiresAtEpochMs())
        assertTrue(legacyCleared)
        assertTrue(legacy.isEmpty())
    }

    @Test
    fun migrateLegacyTokensToSecureStore_skipsWhenSecureAlreadyHasToken() {
        val secure = FakeSecureSessionStore().apply {
            saveTokens(SecureAuthTokens("secure-access", "secure-refresh"))
        }
        var legacyCleared = false

        migrateLegacyTokensToSecureStore(
            secure = secure,
            readLegacyAccessToken = { "legacy-access" },
            readLegacyRefreshToken = { "legacy-refresh" },
            readLegacyExpiresAt = { 0L },
            clearLegacyTokenKeys = { legacyCleared = true },
        )

        assertEquals("secure-access", secure.readAccessToken())
        assertEquals("secure-refresh", secure.readRefreshToken())
        assertEquals(false, legacyCleared)
    }

    @Test
    fun migrateLegacyTokensToSecureStore_noOpWhenLegacyEmpty() {
        val secure = FakeSecureSessionStore()
        var legacyCleared = false

        migrateLegacyTokensToSecureStore(
            secure = secure,
            readLegacyAccessToken = { null },
            readLegacyRefreshToken = { null },
            readLegacyExpiresAt = { 0L },
            clearLegacyTokenKeys = { legacyCleared = true },
        )

        assertNull(secure.readAccessToken())
        assertEquals(false, legacyCleared)
    }
}
