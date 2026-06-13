package com.movit.core.network.contract

/**
 * Single source of truth for legacy Retrofit vs KMP [com.movit.core.network.MovitMobileApi] coverage.
 * Update when adding endpoints (WS-1) or documenting intentional deferrals.
 */
object MobileApiContractRegistry {

    data class DeferredEndpoint(
        val method: String,
        val path: String,
        val reason: String,
    )

    /** Normalized as METHOD + space + path (placeholders: {id}, {slug}, {workoutId}, {sessionId}, {overrideId}, {exerciseId}). */
    val legacyEndpoints: Set<String> = setOf(
        // MobileSyncApi
        "GET api/mobile/sync",
        "GET api/mobile/exercises/{slug}/audio-manifest",
        "GET api/mobile/workout-templates/{slug}/audio-manifest",
        "GET api/mobile/explore",
        "GET api/mobile/home",
        "POST api/assessment",
        "GET api/assessment/latest",
        "GET api/assessment/progress",
        "GET api/mobile/assessment-templates/resolve",
        "GET api/mobile/level-profile",
        "GET api/mobile/level-profile/history",
        "GET api/mobile/level-profile/levels",
        "GET api/mobile/workout-executions/stats",
        "POST api/mobile/prescription/recommend",
        "GET api/mobile/plan",
        "GET api/mobile/plan/today",
        "POST api/mobile/plan/enroll",
        "POST api/mobile/plan/complete",
        "GET api/mobile/plan/enrollment-check",
        "POST api/mobile/plan/pause",
        "POST api/mobile/plan/resume",
        "GET api/mobile/progression/history",
        "GET api/mobile/progression/recent",
        "GET api/mobile/progression/session/{sessionId}",
        "POST api/mobile/progression/mark-seen",
        "GET api/mobile/workout-templates/{id}/training-config",
        "POST api/mobile/workout-executions/explore",
        "GET api/mobile/reassessment/upcoming",
        "POST api/mobile/reassessment/request",
        "GET api/mobile/reports/dashboard",
        "GET api/mobile/reports/metrics",
        "POST api/mobile/planned-workouts/{workoutId}/start",
        "POST api/mobile/planned-workouts/{workoutId}/complete",
        "POST api/mobile/planned-workouts/{workoutId}/report",
        "PUT api/mobile/user-programs/{id}",
        "GET api/mobile/user-programs/{id}/effective-plan",
        "GET api/mobile/user-programs/{id}/overrides",
        "POST api/mobile/user-programs/{id}/overrides",
        "DELETE api/mobile/user-programs/{id}/overrides/{overrideId}",
        "GET api/mobile/user-programs/{id}/progress-metrics",
        "GET api/mobile/programs/{id}/preview",
        "GET api/mobile/exercises/substitutions",
        "GET api/exercises/{id}/substitutions",
        "GET api/mobile/training-profile",
        "PUT api/mobile/training-profile",
        "PUT api/mobile/exercise-preferences/{exerciseId}",
        "DELETE api/mobile/exercise-preferences/{exerciseId}",
        // AuthApi
        "POST api/mobile/auth/register",
        "POST api/mobile/auth/login",
        "POST api/mobile/auth/google",
        "POST api/mobile/auth/refresh",
        "POST api/mobile/auth/logout",
        "POST api/mobile/auth/forgot-password",
        "POST api/mobile/auth/reset-password",
        "GET api/mobile/auth/profile",
        "PATCH api/mobile/auth/profile",
        "PATCH api/mobile/auth/settings",
        // SubscriptionApi
        "GET api/mobile/plans",
        "GET api/mobile/subscriptions/status",
        "GET api/mobile/subscriptions/mine",
        "POST api/mobile/subscriptions/checkout",
        "GET api/mobile/subscriptions/checkout/{id}",
        "POST api/mobile/subscriptions/google-play/verify",
        "POST api/mobile/subscriptions/cancel",
        // BookingApi
        "GET api/bookings/rules",
    )

    /**
     * Legacy mobile consumers **outside** Retrofit (OkHttp constants).
     * Kept in sync via [WorkoutSyncContractPathExtractor] test.
     */
    val legacyNonRetrofitEndpoints: Set<String> = setOf(
        "POST api/mobile/workout-executions",
    )

    /** Retrofit + OkHttp legacy paths that KMP must cover or defer explicitly. */
    val allLegacyConsumerEndpoints: Set<String>
        get() = legacyEndpoints + legacyNonRetrofitEndpoints

    /** Endpoints implemented in MovitMobileApi (must match base("…") paths in source). */
    val kmpCoveredEndpoints: Set<String> = setOf(
        "GET api/mobile/explore",
        "GET api/mobile/home",
        "GET api/mobile/reports/dashboard",
        "GET api/mobile/reports/metrics",
        "GET api/mobile/programs/{id}",
        "GET api/mobile/programs/{id}/preview",
        "GET api/mobile/user-programs/{id}/progress-metrics",
        "GET api/mobile/user-programs/{id}/effective-plan",
        "GET api/mobile/exercises/substitutions",
        "PUT api/mobile/user-programs/{id}",
        "POST api/mobile/auth/login",
        "POST api/mobile/auth/register",
        "POST api/mobile/auth/google",
        "POST api/mobile/auth/forgot-password",
        "POST api/mobile/auth/refresh",
        "POST api/mobile/auth/logout",
        "GET api/mobile/auth/profile",
        "PATCH api/mobile/auth/settings",
        "GET api/mobile/level-profile",
        "GET api/mobile/level-profile/history",
        "GET api/mobile/level-profile/levels",
        "POST api/assessment",
        "GET api/assessment/latest",
        "GET api/assessment/progress",
        "GET api/mobile/assessment-templates/resolve",
        "GET api/mobile/plan",
        "GET api/mobile/plan/today",
        "POST api/mobile/plan/enroll",
        "POST api/mobile/plan/complete",
        "GET api/mobile/sync",
        "GET api/mobile/reassessment/upcoming",
        "GET api/mobile/training-profile",
        "PUT api/mobile/training-profile",
        "GET api/mobile/progression/history",
        "GET api/mobile/progression/recent",
        "GET api/mobile/progression/session/{sessionId}",
        // Phase 07 training block (WS-1)
        "GET api/mobile/workout-templates/{id}/training-config",
        "GET api/mobile/workout-templates/{slug}/audio-manifest",
        "GET api/mobile/exercises/{slug}/audio-manifest",
        "POST api/mobile/planned-workouts/{workoutId}/start",
        "POST api/mobile/planned-workouts/{workoutId}/complete",
        "POST api/mobile/planned-workouts/{workoutId}/report",
        "POST api/mobile/workout-executions",
        "POST api/mobile/workout-executions/explore",
        // Offline writes (WS-1 + WS-2 outbox dispatcher)
        "PUT api/mobile/exercise-preferences/{exerciseId}",
        "DELETE api/mobile/exercise-preferences/{exerciseId}",
        "POST api/mobile/user-programs/{id}/overrides",
        "DELETE api/mobile/user-programs/{id}/overrides/{overrideId}",
        "POST api/mobile/progression/mark-seen",
    )

    val deferredEndpoints: List<DeferredEndpoint> = listOf(
        DeferredEndpoint("POST", "api/mobile/auth/reset-password", "Reset-password flow not in Movit shell"),
        DeferredEndpoint("PATCH", "api/mobile/auth/profile", "Profile PATCH deferred; settings PATCH covered"),
        DeferredEndpoint("GET", "api/mobile/plans", "Subscriptions hidden (StoreKit)"),
        DeferredEndpoint("GET", "api/mobile/subscriptions/status", "Subscriptions hidden"),
        DeferredEndpoint("GET", "api/mobile/subscriptions/mine", "Subscriptions hidden"),
        DeferredEndpoint("POST", "api/mobile/subscriptions/checkout", "Subscriptions hidden"),
        DeferredEndpoint("GET", "api/mobile/subscriptions/checkout/{id}", "Subscriptions hidden"),
        DeferredEndpoint("POST", "api/mobile/subscriptions/google-play/verify", "Subscriptions hidden"),
        DeferredEndpoint("POST", "api/mobile/subscriptions/cancel", "Subscriptions hidden"),
        DeferredEndpoint("GET", "api/bookings/rules", "BookingApi out of Movit product scope"),
        DeferredEndpoint("GET", "api/exercises/{id}/substitutions", "Admin id route; mobile slug route covered"),
        DeferredEndpoint("GET", "api/mobile/workout-executions/stats", "Home stats parity"),
        DeferredEndpoint("POST", "api/mobile/prescription/recommend", "Prescription parity"),
        DeferredEndpoint("GET", "api/mobile/plan/enrollment-check", "Enrollment check parity"),
        DeferredEndpoint("POST", "api/mobile/plan/pause", "No backend route — ActivePlanController has complete only"),
        DeferredEndpoint("POST", "api/mobile/plan/resume", "No backend route — ActivePlanController has complete only"),
        DeferredEndpoint("POST", "api/mobile/reassessment/request", "Reassessment request parity"),
        DeferredEndpoint("GET", "api/mobile/user-programs/{id}/overrides", "Overrides list read parity"),
    )

    val deferredEndpointKeys: Set<String> =
        deferredEndpoints.map { "${it.method} ${it.path}" }.toSet()

    /** KMP-only additions (not in any legacy consumer catalog). */
    val kmpOnlyEndpoints: Set<String> = setOf(
        "GET api/mobile/programs/{id}",
    )
}
