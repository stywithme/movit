package com.movit.feature.library

/**
 * Unified Start CTA readiness for [WorkoutSessionRoute] (and prepare preflight).
 * Controls the dock CTA only — screen content stays visible while preparing.
 */
sealed interface LaunchReadiness {
    /** No displayable session yet. */
    data object LoadingContent : LaunchReadiness

    /** Session visible; training configs still being prepared. */
    data object Preparing : LaunchReadiness

    /** Cached configs ready — Start is cache-only. */
    data object Ready : LaunchReadiness

    /** Can start from cache; upload/sync may happen later. */
    data object OfflineReady : LaunchReadiness

    /** Cannot start until Retry succeeds. */
    data class Blocked(val reasonKey: String) : LaunchReadiness

    /** Start accepted; ignore further taps until navigation or failure. */
    data object Launching : LaunchReadiness
}

fun LaunchReadiness.canStart(): Boolean =
    this is LaunchReadiness.Ready || this is LaunchReadiness.OfflineReady

fun LaunchReadiness.ctaLabelKey(): String = when (this) {
    LaunchReadiness.LoadingContent,
    LaunchReadiness.Preparing,
    -> "session_cta_preparing"
    LaunchReadiness.Ready -> "session_start"
    LaunchReadiness.OfflineReady -> "session_cta_offline_ready"
    is LaunchReadiness.Blocked -> "common_retry"
    LaunchReadiness.Launching -> "session_cta_launching"
}

fun LaunchReadiness.statusLabelKey(): String = when (this) {
    LaunchReadiness.LoadingContent -> "session_loading"
    LaunchReadiness.Preparing -> "session_cta_preparing"
    LaunchReadiness.Ready -> "session_ready_to_train"
    LaunchReadiness.OfflineReady -> "session_cta_offline_ready_hint"
    is LaunchReadiness.Blocked -> reasonKey
    LaunchReadiness.Launching -> "session_cta_launching"
}
