package com.movit.feature.shell

sealed interface MovitAppShellEffect {
    data class ShowMessage(val message: String) : MovitAppShellEffect
    /** Resolved in [MovitAppShellRoute] via [com.movit.resources.localizedString]. */
    data class ShowLocalizedMessage(val key: String) : MovitAppShellEffect
    /** Android handles via the existing legacy subscription screen while the KMP flow is rebuilt. */
    data object LaunchLegacySubscription : MovitAppShellEffect
    /** Strategy B — exit shell and return to legacy [SplashActivity] after logout / session expiry. */
    data object NavigateToLegacyAuth : MovitAppShellEffect
}
