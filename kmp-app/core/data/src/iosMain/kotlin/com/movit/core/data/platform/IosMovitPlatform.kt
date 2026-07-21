package com.movit.core.data.platform

import com.movit.core.data.MovitData
import com.movit.core.data.local.MovitLocalStore
import com.movit.core.data.repository.MovitCacheKeys
import com.movit.core.network.MovitApiConfig
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
class IosMovitPlatform(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
    private val baseUrl: String = MovitApiConfig.getEffectiveBaseUrl(),
    private val secureSession: SecureSessionStore = IosKeychainSecureSessionStore(),
) : MovitPlatformBindings {

    init {
        migrateLegacyTokensFromDefaults()
    }

    override fun apiBaseUrl(): String =
        defaults.stringForKey(KEY_API_BASE_URL)?.takeIf { it.isNotBlank() } ?: baseUrl

    override fun authHeader(): String? {
        val token = secureSession.readAccessToken() ?: return null
        return if (token.startsWith("Bearer ")) token else "Bearer $token"
    }

    override fun userId(): String? =
        defaults.stringForKey(KEY_USER_ID)?.takeIf { it.isNotBlank() }

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

    // Raw cache only — must not call PlanSyncRepository (avoids StackOverflow with readCachedActiveUserProgramId).
    override fun activeUserProgramId(): String? =
        readCache(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.ACTIVE_USER_PROGRAM_ID)
            ?.takeIf { it.isNotBlank() }
            ?: defaults.stringForKey(KEY_ACTIVE_USER_PROGRAM_ID)?.takeIf { it.isNotBlank() }

    override fun setActiveUserProgramId(userProgramId: String?) {
        if (MovitData.isInstalled) {
            val store: MovitLocalStore = MovitData.localStore
            if (userProgramId.isNullOrBlank()) {
                store.remove(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.ACTIVE_USER_PROGRAM_ID)
            } else {
                store.writeJsonCache(
                    MovitCacheKeys.PROGRAM_STORE,
                    MovitCacheKeys.ACTIVE_USER_PROGRAM_ID,
                    userProgramId,
                )
            }
        }
        if (userProgramId.isNullOrBlank()) {
            removeCache(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.ACTIVE_USER_PROGRAM_ID)
            defaults.removeObjectForKey(KEY_ACTIVE_USER_PROGRAM_ID)
        } else {
            writeCache(MovitCacheKeys.PROGRAM_STORE, MovitCacheKeys.ACTIVE_USER_PROGRAM_ID, userProgramId)
            defaults.setObject(userProgramId, KEY_ACTIVE_USER_PROGRAM_ID)
        }
    }

    override fun exerciseImageUrl(slug: String): String? =
        if (MovitData.isInstalled) {
            MovitData.explore.exerciseImageUrl(slug)
        } else {
            null
        }

    override fun userEmail(): String? =
        defaults.stringForKey(KEY_USER_EMAIL)?.takeIf { it.isNotBlank() }

    override fun userAvatarUrl(): String? =
        defaults.stringForKey(KEY_AVATAR_URL)?.takeIf { it.isNotBlank() }

    override fun refreshToken(): String? = secureSession.readRefreshToken()

    override fun tokenExpiresAtEpochMs(): Long = secureSession.readExpiresAtEpochMs()

    override fun updateAuthTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long) {
        secureSession.saveTokens(
            SecureAuthTokens(
                accessToken = accessToken,
                refreshToken = refreshToken,
                expiresAtEpochMs = expiresAtEpochMs,
            ),
        )
    }

    override fun isOnboardingCompleted(): Boolean =
        defaults.boolForKey(KEY_ONBOARDING_COMPLETED)

    override fun totalWorkouts(): Int =
        defaults.integerForKey(KEY_TOTAL_WORKOUTS).toInt()

    override fun totalMinutes(): Int =
        defaults.integerForKey(KEY_TOTAL_MINUTES).toInt()

    override fun subscriptionExpiry(): String? =
        defaults.stringForKey(KEY_SUBSCRIPTION_EXPIRY)?.takeIf { it.isNotBlank() }

    override fun voiceFeedbackEnabled(): Boolean =
        defaults.boolForKey(KEY_VOICE_FEEDBACK)

    override fun notificationsEnabled(): Boolean =
        defaults.boolForKey(KEY_NOTIFICATIONS)

    override fun persistAuthSession(snapshot: AuthSessionSnapshot) {
        val expiresAt = if (snapshot.expiresInSeconds > 0) {
            time(null) * 1000L + snapshot.expiresInSeconds * 1000L
        } else {
            secureSession.readExpiresAtEpochMs()
        }
        secureSession.saveTokens(
            SecureAuthTokens(
                accessToken = snapshot.accessToken,
                refreshToken = snapshot.refreshToken,
                expiresAtEpochMs = expiresAt,
            ),
        )
        defaults.setObject(snapshot.userId, KEY_USER_ID)
        defaults.setObject(snapshot.name, KEY_USER_NAME)
        defaults.setObject(snapshot.email, KEY_USER_EMAIL)
        snapshot.avatarUrl?.let { defaults.setObject(it, KEY_AVATAR_URL) }
        defaults.setObject(snapshot.preferredLanguage, KEY_LANGUAGE)
        defaults.setBool(snapshot.isPro, KEY_IS_PRO)
        defaults.setInteger(snapshot.totalWorkouts.toLong(), KEY_TOTAL_WORKOUTS)
        defaults.setInteger(snapshot.totalMinutes.toLong(), KEY_TOTAL_MINUTES)
        snapshot.subscriptionExpiry?.let { defaults.setObject(it, KEY_SUBSCRIPTION_EXPIRY) }
        defaults.setBool(snapshot.voiceFeedback, KEY_VOICE_FEEDBACK)
        defaults.setBool(snapshot.notifications, KEY_NOTIFICATIONS)
        clearLegacyTokenKeysFromDefaults()
    }

    override fun clearAuthSession() {
        secureSession.clearTokens()
        clearLegacyTokenKeysFromDefaults()
        defaults.removeObjectForKey(KEY_USER_ID)
        defaults.removeObjectForKey(KEY_USER_EMAIL)
        defaults.removeObjectForKey(KEY_AVATAR_URL)
        defaults.removeObjectForKey(KEY_SUBSCRIPTION_EXPIRY)
        defaults.removeObjectForKey(KEY_ACTIVE_USER_PROGRAM_ID)
        defaults.setBool(false, KEY_IS_PRO)
        defaults.setBool(false, KEY_ONBOARDING_COMPLETED)
    }

    override fun clearLegacyUserCaches() {
        MovitLegacyUserCacheStores.sharedPreferenceNames.forEach { store ->
            clearCacheStore(store)
        }
    }

    override fun clearUserFiles() {
        val fm = NSFileManager.defaultManager
        val docs = fm.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
            .firstOrNull() as? platform.Foundation.NSURL
            ?: return
        listOf("frame_captures", "audio_cache").forEach { dirName ->
            val url = docs.URLByAppendingPathComponent(dirName) ?: return@forEach
            runCatching { fm.removeItemAtURL(url, null) }
        }
    }

    override fun cleanupOrphanFrameCaptures(protectedSessionIds: Set<String>): Int {
        val fm = NSFileManager.defaultManager
        val docs = fm.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
            .firstOrNull() as? platform.Foundation.NSURL
            ?: return 0
        val root = docs.URLByAppendingPathComponent("frame_captures") ?: return 0
        val path = root.path ?: return 0
        if (!fm.fileExistsAtPath(path)) return 0
        val children = fm.contentsOfDirectoryAtPath(path, null) as? List<*> ?: return 0
        var removed = 0
        for (name in children) {
            val sessionId = name as? String ?: continue
            if (sessionId in protectedSessionIds) continue
            val sessionPath = "$path/$sessionId"
            if (fm.fileExistsAtPath(sessionPath)) {
                runCatching {
                    fm.removeItemAtPath(sessionPath, null)
                    removed++
                }
            }
        }
        return removed
    }

    override fun setOnboardingCompleted(completed: Boolean) {
        defaults.setBool(completed, KEY_ONBOARDING_COMPLETED)
    }

    override fun updateUserSettings(
        preferredLanguage: String?,
        voiceFeedback: Boolean?,
        notifications: Boolean?,
    ) {
        preferredLanguage?.let { defaults.setObject(it, KEY_LANGUAGE) }
        voiceFeedback?.let { defaults.setBool(it, KEY_VOICE_FEEDBACK) }
        notifications?.let { defaults.setBool(it, KEY_NOTIFICATIONS) }
    }

    override fun themeMode(): String =
        defaults.stringForKey(KEY_THEME_MODE)?.takeIf { it.isNotBlank() }
            ?: MovitThemeModeStorage.SYSTEM

    override fun setThemeMode(mode: String) {
        val normalized = when (mode.lowercase()) {
            MovitThemeModeStorage.LIGHT -> MovitThemeModeStorage.LIGHT
            MovitThemeModeStorage.DARK -> MovitThemeModeStorage.DARK
            else -> MovitThemeModeStorage.SYSTEM
        }
        defaults.setObject(normalized, KEY_THEME_MODE)
    }

    override fun applyPreferredLanguage(languageCode: String) {
        defaults.setObject(languageCode, KEY_LANGUAGE)
    }

    override fun isNetworkAvailable(): Boolean {
        IosNetworkMonitor.ensureStarted()
        return IosNetworkMonitor.isOnline
    }

    private fun migrateLegacyTokensFromDefaults() {
        migrateLegacyTokensToSecureStore(
            secure = secureSession,
            readLegacyAccessToken = { defaults.stringForKey(LegacyAuthTokenKeys.ACCESS_TOKEN) },
            readLegacyRefreshToken = { defaults.stringForKey(LegacyAuthTokenKeys.REFRESH_TOKEN) },
            readLegacyExpiresAt = { 0L },
            clearLegacyTokenKeys = { clearLegacyTokenKeysFromDefaults() },
        )
    }

    private fun clearLegacyTokenKeysFromDefaults() {
        defaults.removeObjectForKey(LegacyAuthTokenKeys.ACCESS_TOKEN)
        defaults.removeObjectForKey(LegacyAuthTokenKeys.REFRESH_TOKEN)
        defaults.removeObjectForKey(LegacyAuthTokenKeys.TOKEN_EXPIRES_AT)
    }

    private fun cacheKey(store: String, key: String): String = "movit_cache_${store}_$key"

    private fun cacheStorePrefix(store: String): String = "movit_cache_${store}_"

    private fun clearCacheStore(store: String) {
        val prefix = cacheStorePrefix(store)
        defaults.dictionaryRepresentation().keys
            .mapNotNull { it as? String }
            .filter { it.startsWith(prefix) }
            .forEach { defaults.removeObjectForKey(it) }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://back.mongz.online/"
        private const val KEY_API_BASE_URL = "movit_api_base_url"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_AVATAR_URL = "avatar_url"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_IS_PRO = "is_pro"
        private const val KEY_ACTIVE_USER_PROGRAM_ID = "active_user_program_id"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_TOTAL_WORKOUTS = "total_workouts"
        private const val KEY_TOTAL_MINUTES = "total_minutes"
        private const val KEY_SUBSCRIPTION_EXPIRY = "subscription_expiry"
        private const val KEY_VOICE_FEEDBACK = "voice_feedback_enabled"
        private const val KEY_NOTIFICATIONS = "notifications_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
