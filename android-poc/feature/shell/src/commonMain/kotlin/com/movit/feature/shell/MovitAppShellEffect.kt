package com.movit.feature.shell

sealed interface MovitAppShellEffect {
    data class ShowMessage(val message: String) : MovitAppShellEffect
    /** Resolved in [MovitAppShellRoute] via [com.movit.resources.localizedString]. */
    data class ShowLocalizedMessage(val key: String) : MovitAppShellEffect
    /** Phase 07 boundary — Android handles via [LegacyTrainingLauncher]. */
    data class LaunchLegacyCameraTraining(
        val exerciseFileName: String,
        val poseVariant: Int = 0,
    ) : MovitAppShellEffect
    /** Android handles via the existing legacy subscription screen while the KMP flow is rebuilt. */
    data object LaunchLegacySubscription : MovitAppShellEffect
}
