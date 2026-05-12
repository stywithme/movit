package com.trainingvalidator.poc.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url
import okhttp3.ResponseBody

/**
 * Mobile Sync API Interface
 * 
 * Retrofit interface for the mobile sync endpoints.
 */
interface MobileSyncApi {
    
    /**
     * Sync exercises with the server.
     * 
     * @param updatedAfter Optional ISO timestamp for incremental sync
     * @param forceRefresh Force full sync, ignoring updatedAfter
     * @return MobileSyncResponse with exercises and audio manifest
     */
    @GET("api/mobile/sync")
    suspend fun sync(
        @Header("Authorization") authorization: String? = null,
        @Query("updatedAfter") updatedAfter: String? = null,
        @Query("forceRefresh") forceRefresh: Boolean? = null
    ): Response<MobileSyncResponse>

    /**
     * Per-exercise audio manifest (URLs that already exist on server; no generation).
     */
    @GET("api/mobile/exercises/{slug}/audio-manifest")
    suspend fun getExerciseAudioManifest(
        @Path("slug") slug: String,
        @Header("Authorization") authorization: String?
    ): Response<EntityAudioManifestApiResponse>

    /**
     * Per-workout audio manifest (union of referenced exercises + system messages).
     */
    @GET("api/mobile/workouts/{slug}/audio-manifest")
    suspend fun getWorkoutAudioManifest(
        @Path("slug") slug: String,
        @Header("Authorization") authorization: String?
    ): Response<EntityAudioManifestApiResponse>

    /**
     * Unified explore endpoint for Home/Explore previews.
     * Supports incremental sync by `updatedAfter`.
     */
    @GET("api/mobile/explore")
    suspend fun getExplore(
        @Header("Authorization") authorization: String? = null,
        @Query("updatedAfter") updatedAfter: String? = null,
        @Query("limit") limit: Int? = null
    ): Response<ExploreResponse>

    /**
     * Unified home endpoint for the dashboard.
     * Returns stats, level profile, active plan, and today's plan.
     */
    @GET("api/mobile/home")
    suspend fun getHomeData(
        @Header("Authorization") authorization: String
    ): Response<HomeResponse>

    
    /**
     * Download an audio file.
     * 
     * @param url Full URL or path to the audio file
     * @return ResponseBody containing the audio data
     */
    @Streaming
    @GET
    suspend fun downloadAudio(@Url url: String): Response<ResponseBody>

    // ─── Assessment Endpoints ────────────────────────────────────

    /**
     * Upload a Body Scan assessment result.
     */
    @POST("api/assessment")
    suspend fun uploadAssessment(
        @Header("Authorization") authorization: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any?>
    ): Response<AssessmentUploadResponse>

    /**
     * Get the latest assessment for the authenticated user.
     */
    @GET("api/assessment/latest")
    suspend fun getLatestAssessment(
        @Header("Authorization") authorization: String
    ): Response<AssessmentApiResponse>

    /**
     * Get assessment progress (current vs previous comparison).
     */
    @GET("api/assessment/progress")
    suspend fun getAssessmentProgress(
        @Header("Authorization") authorization: String
    ): Response<AssessmentProgressResponse>

    // ─── Assessment Template Endpoints ────────────────────────────

    /**
     * Resolve the appropriate assessment template for the user's current level.
     */
    @GET("api/mobile/assessment-templates/resolve")
    suspend fun resolveAssessmentTemplate(
        @Header("Authorization") authorization: String,
        @Query("mode") mode: String? = null
    ): Response<AssessmentTemplateResponse>

    // ─── Level Profile Endpoints ──────────────────────────────

    /**
     * Get the user's current level profile.
     */
    @GET("api/mobile/level-profile")
    suspend fun getLevelProfile(
        @Header("Authorization") authorization: String
    ): Response<LevelProfileResponse>

    /**
     * Get level profile history (all previous profiles for comparison).
     */
    @GET("api/mobile/level-profile/history")
    suspend fun getLevelProfileHistory(
        @Header("Authorization") authorization: String
    ): Response<LevelProfileHistoryResponse>

    /**
     * Get all level definitions.
     */
    @GET("api/mobile/level-profile/levels")
    suspend fun getLevels(
        @Header("Authorization") authorization: String
    ): Response<LevelsListResponse>

    // ─── User Stats Endpoint ───────────────────────────────────

    /**
     * Get user home stats (weekly workouts, form score, streak).
     */
    @GET("api/mobile/sessions/stats")
    suspend fun getUserStats(
        @Header("Authorization") authorization: String
    ): Response<UserStatsResponse>

    // ─── Prescription Endpoint ────────────────────────────────────

    /**
     * Get recommended program based on latest assessment.
     */
    @POST("api/mobile/prescription/recommend")
    suspend fun getRecommendation(
        @Header("Authorization") authorization: String
    ): Response<PrescriptionResponse>

    // ─── Active Plan Endpoints ──────────────────────────────────

    /**
     * Get user's active plan.
     */
    @GET("api/mobile/plan")
    suspend fun getActivePlan(
        @Header("Authorization") authorization: String
    ): Response<ActivePlanResponse>

    /**
     * Get today's training plan.
     */
    @GET("api/mobile/plan/today")
    suspend fun getTodayPlan(
        @Header("Authorization") authorization: String
    ): Response<TodayPlanResponse>

    /**
     * Enroll in a program (adds to active plan).
     */
    @POST("api/mobile/plan/enroll")
    suspend fun enrollProgram(
        @Header("Authorization") authorization: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Response<ActivePlanResponse>

    /**
     * Complete the active program and transition to next.
     */
    @POST("api/mobile/plan/complete")
    suspend fun completeActiveProgram(
        @Header("Authorization") authorization: String
    ): Response<ActivePlanResponse>

    @GET("api/mobile/plan/enrollment-check")
    suspend fun enrollmentCheck(
        @Header("Authorization") authorization: String,
        @Query("programId") programId: String
    ): Response<EnrollmentCheckApiResponse>

    @POST("api/mobile/plan/pause")
    suspend fun pausePlan(
        @Header("Authorization") authorization: String
    ): Response<Map<String, @JvmSuppressWildcards Any?>>

    @POST("api/mobile/plan/resume")
    suspend fun resumePlan(
        @Header("Authorization") authorization: String
    ): Response<Map<String, @JvmSuppressWildcards Any?>>

    // ─── Progression Endpoints ──────────────────────────────────

    /**
     * Get progression history (all changes made by the Progression Engine).
     */
    @GET("api/mobile/progression/history")
    suspend fun getProgressionHistory(
        @Header("Authorization") authorization: String
    ): Response<ProgressionHistoryResponse>

    /**
     * Get recent unseen progression changes — used for post-session notifications.
     */
    @GET("api/mobile/progression/recent")
    suspend fun getRecentProgression(
        @Header("Authorization") authorization: String
    ): Response<ProgressionHistoryResponse>

    /**
     * Mark progression changes as seen/acknowledged.
     */
    @POST("api/mobile/progression/mark-seen")
    suspend fun markProgressionSeen(
        @Header("Authorization") authorization: String,
        @Body payload: ProgressionMarkSeenRequest
    ): Response<ProgressionMarkSeenResponse>

    /**
     * Get workout training configuration (full exercise data for the training engine).
     */
    @GET("api/mobile/workouts/{id}/training-config")
    suspend fun getWorkoutTrainingConfig(
        @Header("Authorization") authorization: String,
        @Path("id") workoutId: String
    ): Response<okhttp3.ResponseBody>

    /**
     * Upload a multi-exercise free session (Explore / Quick Start mode).
     */
    @POST("api/mobile/sessions/explore")
    suspend fun uploadExploreSession(
        @Header("Authorization") authorization: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any?>
    ): Response<okhttp3.ResponseBody>

    // ─── Reassessment Endpoints ─────────────────────────────────

    /**
     * Get upcoming reassessment schedules.
     */
    @GET("api/mobile/reassessment/upcoming")
    suspend fun getUpcomingReassessments(
        @Header("Authorization") authorization: String
    ): Response<ReassessmentListResponse>

    /**
     * Manually request a reassessment.
     */
    @POST("api/mobile/reassessment/request")
    suspend fun requestReassessment(
        @Header("Authorization") authorization: String
    ): Response<ReassessmentResponse>

    // ─── Unified Reports Endpoint ────────────────────────────────

    /**
     * Coach-style dashboard payload for the redesigned Reports tab.
     */
    @GET("api/mobile/reports/dashboard")
    suspend fun getReportsDashboard(
        @Header("Authorization") authorization: String,
        @Query("programId") programId: String? = null,
        @Query("period") period: String? = null,
        @Query("source") source: String? = null,
        @Query("exerciseSlug") exerciseSlug: String? = null
    ): Response<ReportDashboardResponse>

    /**
     * Unified metrics endpoint — returns aggregated metrics at any scope.
     * scope: program | week | day | session | exercise
     */
    @GET("api/mobile/reports/metrics")
    suspend fun getMetrics(
        @Header("Authorization") authorization: String,
        @Query("programId") programId: String,
        @Query("scope") scope: String,
        @Query("weekNumber") weekNumber: Int? = null,
        @Query("dayNumber") dayNumber: Int? = null,
        @Query("sessionId") sessionId: String? = null,
        @Query("exerciseSlug") exerciseSlug: String? = null,
        @Query("includeHistory") includeHistory: Boolean? = null,
        @Query("includeChildren") includeChildren: Boolean? = null
    ): Response<MetricsResponse>

    // ─── Program Session Endpoints ──────────────────────────────

    /**
     * Notify server that a program session has started.
     */
    @POST("api/mobile/sessions/{sessionId}/start")
    suspend fun startSession(
        @Path("sessionId") sessionId: String,
        @Header("Authorization") authorization: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    /**
     * Notify server that a program session has been completed.
     */
    @POST("api/mobile/sessions/{sessionId}/complete")
    suspend fun completeSession(
        @Path("sessionId") sessionId: String,
        @Header("Authorization") authorization: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    /**
     * Submit a detailed session report.
     */
    @POST("api/mobile/sessions/{sessionId}/report")
    suspend fun reportSession(
        @Path("sessionId") sessionId: String,
        @Header("Authorization") authorization: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    // ─── User Program Customization Endpoints ─────────────────

    /**
     * Update user program customizations (session modifications, reorders, etc.)
     */
    @PUT("api/mobile/user-programs/{id}")
    suspend fun updateUserProgram(
        @Path("id") userProgramId: String,
        @Header("Authorization") authorization: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Response<ResponseBody>

    @GET("api/mobile/user-programs/{id}/effective-plan")
    suspend fun getEffectivePlan(
        @Path("id") userProgramId: String,
        @Header("Authorization") authorization: String,
        @Query("week") week: Int,
        @Query("day") day: Int
    ): Response<EffectivePlanApiResponse>

    @GET("api/mobile/user-programs/{id}/overrides")
    suspend fun listUserProgramOverrides(
        @Path("id") userProgramId: String,
        @Header("Authorization") authorization: String
    ): Response<UserProgramOverridesApiResponse>

    @POST("api/mobile/user-programs/{id}/overrides")
    suspend fun createUserProgramOverride(
        @Path("id") userProgramId: String,
        @Header("Authorization") authorization: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<UserProgramOverrideCreateApiResponse>

    @DELETE("api/mobile/user-programs/{id}/overrides/{overrideId}")
    suspend fun deleteUserProgramOverride(
        @Path("id") userProgramId: String,
        @Path("overrideId") overrideId: String,
        @Header("Authorization") authorization: String
    ): Response<ResponseBody>

    @POST("api/mobile/user-programs/{id}/complete")
    suspend fun completeUserProgram(
        @Path("id") userProgramId: String,
        @Header("Authorization") authorization: String
    ): Response<ProgramCompleteApiResponse>

    @GET("api/mobile/user-programs/{id}/progress-metrics")
    suspend fun getProgramProgressMetrics(
        @Path("id") userProgramId: String,
        @Header("Authorization") authorization: String
    ): Response<ProgramProgressMetricsApiResponse>

    @GET("api/mobile/programs/{id}/preview")
    suspend fun getProgramPreview(@Path("id") programId: String): Response<ProgramPreviewApiResponse>

    @GET("api/mobile/exercises/substitutions")
    suspend fun getSubstitutionExercises(
        @Header("Authorization") authorization: String,
        @Query("slug") slug: String,
        @Query("limit") limit: Int? = null
    ): Response<SubstitutionExercisesApiResponse>

    /** Same substitution logic as admin/mobile slug endpoint, keyed by exercise id. */
    @GET("api/exercises/{id}/substitutions")
    suspend fun getSubstitutionsByExerciseId(
        @Path("id") exerciseId: String,
        @Header("Authorization") authorization: String,
    ): Response<SubstitutionExercisesApiResponse>

    @GET("api/mobile/training-profile")
    suspend fun getTrainingProfile(
        @Header("Authorization") authorization: String
    ): Response<TrainingProfileApiResponse>

    @PUT("api/mobile/training-profile")
    suspend fun putTrainingProfile(
        @Header("Authorization") authorization: String,
        @Body body: Map<String, @JvmSuppressWildcards Any?>
    ): Response<TrainingProfileApiResponse>

    // ─── User exercise preferences (standalone targets) ─────────────────

    @PUT("api/mobile/exercise-preferences/{exerciseId}")
    suspend fun upsertExercisePreference(
        @Path("exerciseId") exerciseId: String,
        @Header("Authorization") authorization: String,
        @Body body: UserExercisePreferenceUpsertRequest
    ): Response<ExercisePreferenceApiResponse>

    @DELETE("api/mobile/exercise-preferences/{exerciseId}")
    suspend fun deleteExercisePreference(
        @Path("exerciseId") exerciseId: String,
        @Header("Authorization") authorization: String
    ): Response<ResponseBody>
}
