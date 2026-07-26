package com.movit.core.data.access

import com.movit.core.data.MovitData

/**
 * Who may open the Training Debug Lab.
 *
 * Until now the lab was gated purely on build type (`BuildConfig.DEBUG` / `Platform.isDebugBinary`),
 * which makes it unreachable for the owner running a production build. This adds an identity gate
 * on top: the primary admin account keeps access in any build.
 *
 * **Security note — read before extending this.** This is a *client-side* check on values the app
 * itself persisted at sign-in. It hides an internal diagnostics screen from ordinary users; it is
 * not an authorisation boundary, and anyone who can modify the app or its preferences can defeat
 * it. That is acceptable here because the lab only visualises the pose data already flowing through
 * the device. **Do not** put anything privileged (other users' data, admin actions, secrets) behind
 * this gate — when that day comes, move the decision server-side: the backend already has a roles
 * table, it simply is not surfaced in `UserPublicDto` yet.
 */
object TrainingDebugAccess {
    /** Backend id of the founding admin account. */
    private const val PRIMARY_ADMIN_USER_ID = "1"

    private val ADMIN_EMAILS = setOf("alustadh.manager@gmail.com")

    /** Pure predicate — no platform access, so it is directly testable. */
    fun isPrimaryAdmin(userId: String?, email: String?): Boolean {
        val id = userId?.trim()
        if (!id.isNullOrEmpty() && id == PRIMARY_ADMIN_USER_ID) return true
        val normalizedEmail = email?.trim()?.lowercase()
        return !normalizedEmail.isNullOrEmpty() && normalizedEmail in ADMIN_EMAILS
    }

    /**
     * Whether the lab entry point should exist at all.
     *
     * @param platformSupportsLab `PlatformInfo.supportsTrainingDebugLab` — the build/platform
     *   capability. Passed in rather than read here so this stays free of module dependencies
     *   and directly unit-testable.
     */
    fun isLabAvailable(platformSupportsLab: Boolean): Boolean =
        platformSupportsLab || isPrimaryAdmin()

    /** Reads the signed-in identity from the persisted auth profile. `false` when signed out. */
    fun isPrimaryAdmin(): Boolean {
        if (!MovitData.isInstalled) return false
        val platform = runCatching { MovitData.requirePlatform() }.getOrNull() ?: return false
        return isPrimaryAdmin(
            userId = runCatching { platform.userId() }.getOrNull(),
            email = runCatching { platform.userEmail() }.getOrNull(),
        )
    }
}
