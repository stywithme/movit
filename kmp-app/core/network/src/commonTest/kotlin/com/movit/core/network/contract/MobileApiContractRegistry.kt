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

    data class BackendOnlyEndpoint(
        val method: String,
        val path: String,
        val reason: String,
    )

    /** Normalized as METHOD + space + path (placeholders: {id}, {slug}, {workoutId}, {sessionId}, {overrideId}, {exerciseId}). */
    // Legacy Retrofit fully removed (WS-D/B8): AuthApi/ApiClient deleted; auth endpoints are now
    // KMP-native in MovitMobileApi (see kmpCoveredEndpoints), subscriptions in MovitBillingApi.
    val legacyEndpoints: Set<String> = emptySet()

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

    /** Endpoints implemented in MovitBillingApi. */
    val kmpBillingCoveredEndpoints: Set<String> = setOf(
        "GET api/mobile/plans",
        "GET api/mobile/subscriptions/status",
        "POST api/mobile/subscriptions/checkout",
        "GET api/mobile/subscriptions/checkout/{checkoutId}",
        "POST api/mobile/subscriptions/google-play/verify",
        "POST api/mobile/subscriptions/app-store/verify",
        "POST api/mobile/subscriptions/cancel",
    )

    /** Endpoints implemented in MovitMobileApi (must match base("...") paths in source). */
    val kmpMobileCoveredEndpoints: Set<String> = setOf(
        "GET api/mobile/explore",
        "GET api/mobile/home",
        "GET api/mobile/reports/dashboard",
        "GET api/mobile/reports/metrics",
        "GET api/mobile/programs/{id}",
        "GET api/mobile/programs/{id}/preview",
        "GET api/mobile/user-programs/{id}/progress-metrics",
        "GET api/mobile/user-programs/{id}/effective-plan",
        "GET api/mobile/user-programs",
        "GET api/mobile/exercises/substitutions",
        "PUT api/mobile/user-programs/{id}",
        "POST api/mobile/auth/login",
        "POST api/mobile/auth/register",
        "POST api/mobile/auth/google",
        "POST api/mobile/auth/forgot-password",
        "POST api/mobile/auth/refresh",
        "POST api/mobile/auth/logout",
        "DELETE api/mobile/auth/account",
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

    val kmpCoveredEndpoints: Set<String>
        get() = kmpMobileCoveredEndpoints + kmpBillingCoveredEndpoints

    /**
     * Backend mobile routes with no KMP API method and no planned near-term consumer.
     * Catalog/list routes are satisfied via `/sync` and `/explore` instead of list endpoints.
     */
    val backendOnlyEndpoints: List<BackendOnlyEndpoint> = listOf(
        BackendOnlyEndpoint("DELETE", "api/mobile/auth/logout", "POST logout used; DELETE kept for admin parity"),
        BackendOnlyEndpoint("POST", "api/mobile/auth/change-password", "Account security screen not in Movit shell"),
        BackendOnlyEndpoint("GET", "api/mobile/programs", "Program catalog via sync/explore"),
        BackendOnlyEndpoint("POST", "api/mobile/programs/{id}/enroll", "Enrollment via POST api/mobile/plan/enroll"),
        BackendOnlyEndpoint("GET", "api/mobile/workout-templates", "Workout catalog via sync/explore"),
        BackendOnlyEndpoint("GET", "api/mobile/subscriptions/mine", "Subscription history UI not ported"),
        BackendOnlyEndpoint("GET", "api/mobile/exercise-preferences", "Preferences hydrated from sync payload"),
        BackendOnlyEndpoint("GET", "api/mobile/workout-executions", "Execution history UI not ported"),
        BackendOnlyEndpoint("GET", "api/mobile/workout-executions/{exerciseId}", "Per-exercise history UI not ported"),
        BackendOnlyEndpoint("GET", "api/mobile/user-programs/{id}/today", "Today plan via GET api/mobile/plan/today"),
        BackendOnlyEndpoint("POST", "api/mobile/user-programs/{id}/complete", "Completion via POST api/mobile/plan/complete"),
        BackendOnlyEndpoint("GET", "api/mobile/reassessment/history", "Reassessment history UI not ported"),
        BackendOnlyEndpoint("GET", "api/mobile/progression/planned-workout/{id}", "Alias of progression/session/{sessionId}"),
        BackendOnlyEndpoint("GET", "api/assessment/history", "Assessment history UI not ported"),
        BackendOnlyEndpoint("GET", "api/assessment/{id}", "Assessment detail UI not ported"),
        BackendOnlyEndpoint("DELETE", "api/assessment/{id}", "Assessment delete needs product decision"),
        BackendOnlyEndpoint("GET", "api/exercises/{id}/substitutions", "Admin id route; mobile slug route covered"),
    )

    val backendOnlyEndpointKeys: Set<String> =
        backendOnlyEndpoints.map { "${it.method} ${it.path}" }.toSet()

    val deferredEndpoints: List<DeferredEndpoint> = listOf(
        DeferredEndpoint("POST", "api/mobile/auth/reset-password", "Reset-password flow not in Movit shell"),
        DeferredEndpoint("PATCH", "api/mobile/auth/profile", "Profile PATCH deferred; settings PATCH covered"),
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

    /**
     * Canonical backend mobile route inventory (75-row parity matrix + assessment progress).
     * Every entry must be KMP-covered, deferred, or backend-only.
     */
    val backendMobileRouteInventory: Set<String>
        get() = kmpCoveredEndpoints +
            deferredEndpointKeys +
            backendOnlyEndpointKeys +
            setOf("GET api/assessment/progress")

    /** KMP-only additions (not in any legacy consumer catalog). */
    val kmpOnlyEndpoints: Set<String> = setOf(
        "GET api/mobile/programs/{id}",
    )
}
