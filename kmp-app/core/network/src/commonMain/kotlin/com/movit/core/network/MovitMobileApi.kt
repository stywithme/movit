package com.movit.core.network

import com.movit.core.network.dto.AuthApiResponse
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import com.movit.core.network.dto.AuthDataDto
import com.movit.core.network.dto.AuthTokensDto
import com.movit.core.network.dto.ExploreApiResponse
import com.movit.core.network.dto.ExploreWorkoutApiResponse
import com.movit.core.network.dto.ExploreWorkoutUploadRequestDto
import com.movit.core.network.dto.WorkoutExecutionApiResponse
import com.movit.core.network.dto.WorkoutExecutionUploadRequestDto
import com.movit.core.network.dto.EntityAudioManifestApiResponse
import com.movit.core.network.dto.ForgotPasswordRequestDto
import com.movit.core.network.dto.GoogleAuthRequestDto
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.EffectivePlanApiResponse
import com.movit.core.network.dto.ActivePlanApiResponse
import com.movit.core.network.dto.AssessmentApiResponse
import com.movit.core.network.dto.AssessmentProgressApiResponse
import com.movit.core.network.dto.AssessmentTemplateApiResponse
import com.movit.core.network.dto.BodyScanUploadRequestDto
import com.movit.core.network.dto.EnrollProgramRequestDto
import com.movit.core.network.dto.MobileSyncApiResponse
import com.movit.core.network.dto.LevelProfileApiResponse
import com.movit.core.network.dto.ReassessmentListApiResponse
import com.movit.core.network.dto.LoginRequestDto
import com.movit.core.network.dto.LogoutRequestDto
import com.movit.core.network.dto.MetricsApiResponse
import com.movit.core.network.dto.MetricsQuery
import com.movit.core.network.dto.MetricsScope
import com.movit.core.network.dto.PlannedWorkoutApiResponse
import com.movit.core.network.dto.PlannedWorkoutCompleteRequestDto
import com.movit.core.network.dto.PlannedWorkoutStartRequestDto
import com.movit.core.network.dto.PlanCompleteRequestDto
import com.movit.core.network.dto.PlanMutationResponse
import com.movit.core.network.dto.LevelProfileHistoryApiResponse
import com.movit.core.network.dto.LevelsListApiResponse
import com.movit.core.network.dto.ProgramExportApiResponse
import com.movit.core.network.dto.ProgramPreviewApiResponse
import com.movit.core.network.dto.ProgramProgressMetricsApiResponse
import com.movit.core.network.dto.ProgressionHistoryApiResponse
import com.movit.core.network.dto.ProgressionMarkSeenRequest
import com.movit.core.network.dto.ProgressionMarkSeenResponse
import com.movit.core.network.dto.TodayPlanApiResponse
import com.movit.core.network.dto.RefreshTokenRequestDto
import com.movit.core.network.dto.UserExercisePreferenceUpsertRequest
import com.movit.core.network.dto.UserProgramOverrideCreateRequest
import com.movit.core.network.dto.UserProgramOverrideCreateResponse
import com.movit.core.network.dto.RegisterRequestDto
import com.movit.core.network.dto.ReportsDashboardApiResponse
import com.movit.core.network.dto.SubstitutionExercisesApiResponse
import com.movit.core.network.dto.TrainingConfigApiResponse
import com.movit.core.network.dto.TrainingProfileApiResponse
import com.movit.core.network.dto.TrainingProfilePutRequest
import com.movit.core.network.dto.UpdateSettingsRequestDto
import com.movit.core.network.dto.UserProgramExportDto
import com.movit.core.network.dto.UserProgramsApiResponse
import com.movit.core.network.dto.UserProgramUpdateRequest
import com.movit.core.network.dto.UserPublicDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess

class MovitMobileApi(
    private val client: HttpClient,
    private val baseUrlProvider: () -> String,
) {
    private fun base(path: String): String {
        val root = baseUrlProvider().trimEnd('/')
        val normalized = path.removePrefix("/")
        return "$root/$normalized"
    }

    private fun HttpRequestBuilder.applyBearerAuthorization(authorization: String?) {
        authorization?.let { header("Authorization", it) }
    }

    suspend fun fetchExplore(
        authorization: String?,
        updatedAfter: String?,
        limit: Int?,
    ): Result<ExploreApiResponse> = runCatching {
        val response = client.get(base("api/mobile/explore")) {
            authorization?.let { header("Authorization", it) }
            updatedAfter?.let { parameter("updatedAfter", it) }
            limit?.let { parameter("limit", it) }
        }
        ensureSuccess(response, "Explore request failed")
        response.body<ExploreApiResponse>()
    }

    /**
     * @param ifNoneMatch optional ETag from a prior home response (P2.4).
     * On 304, returns [HomeApiResponse] with `success=true` and `data=null` (caller keeps cache).
     */
    suspend fun fetchHome(
        authorization: String? = null,
        ifNoneMatch: String? = null,
    ): Result<HomeApiResponse> = runCatching {
        val response = client.get(base("api/mobile/home")) {
            applyBearerAuthorization(authorization)
            ifNoneMatch?.takeIf { it.isNotBlank() }?.let { header("If-None-Match", it) }
        }
        if (response.status.value == 304) {
            return@runCatching HomeApiResponse(success = true, data = null)
        }
        ensureSuccess(response, "Home request failed")
        val body = response.body<HomeApiResponse>()
        val etag = response.headers["ETag"]
        if (etag != null && body.data != null) {
            body.copy(etag = etag)
        } else {
            body
        }
    }

    suspend fun fetchReportsDashboard(
        programId: String?,
        period: String,
        source: String,
        authorization: String? = null,
    ): Result<ReportsDashboardApiResponse> = runCatching {
        val response = client.get(base("api/mobile/reports/dashboard")) {
            applyBearerAuthorization(authorization)
            programId?.let { parameter("programId", it) }
            parameter("period", period)
            parameter("source", source)
        }
        ensureSuccess(response, "Reports dashboard request failed")
        response.body<ReportsDashboardApiResponse>()
    }

    suspend fun fetchMetrics(
        query: MetricsQuery,
        authorization: String? = null,
    ): Result<MetricsApiResponse> = runCatching {
        val response = client.get(base("api/mobile/reports/metrics")) {
            applyBearerAuthorization(authorization)
            parameter("programId", query.programId)
            parameter("scope", query.scope.wireValue)
            query.weekNumber?.let { parameter("weekNumber", it) }
            query.dayNumber?.let { parameter("dayNumber", it) }
            query.plannedWorkoutId?.let { parameter("plannedWorkoutId", it) }
            query.exerciseSlug?.let { parameter("exerciseSlug", it) }
            if (query.includeHistory) {
                parameter("includeHistory", true)
            }
            if (query.includeChildren) {
                parameter("includeChildren", true)
            }
        }
        ensureSuccess(response, "Metrics request failed")
        response.body<MetricsApiResponse>()
    }

    suspend fun fetchExerciseMetrics(
        programId: String,
        exerciseSlug: String,
        authorization: String? = null,
    ): Result<MetricsApiResponse> = fetchMetrics(
        MetricsQuery(
            programId = programId,
            scope = MetricsScope.Exercise,
            exerciseSlug = exerciseSlug,
            includeHistory = true,
        ),
        authorization = authorization,
    )

    suspend fun fetchProgram(
        programId: String,
        authorization: String? = null,
    ): Result<ProgramExportApiResponse> = runCatching {
        val response = client.get(base("api/mobile/programs/$programId")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Program request failed")
        response.body<ProgramExportApiResponse>()
    }

    suspend fun fetchProgramPreview(
        programId: String,
        authorization: String? = null,
    ): Result<ProgramPreviewApiResponse> = runCatching {
        val response = client.get(base("api/mobile/programs/$programId/preview")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Program preview request failed")
        response.body<ProgramPreviewApiResponse>()
    }

    suspend fun fetchProgramProgressMetrics(
        userProgramId: String,
        authorization: String? = null,
    ): Result<ProgramProgressMetricsApiResponse> = runCatching {
        val response = client.get(base("api/mobile/user-programs/$userProgramId/progress-metrics")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Program progress metrics request failed")
        response.body<ProgramProgressMetricsApiResponse>()
    }

    suspend fun fetchEffectivePlan(
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
        authorization: String? = null,
    ): Result<EffectivePlanApiResponse> = runCatching {
        val response = client.get(base("api/mobile/user-programs/$userProgramId/effective-plan")) {
            applyBearerAuthorization(authorization)
            parameter("week", weekNumber)
            parameter("day", dayNumber)
        }
        ensureSuccess(response, "Effective plan request failed")
        response.body<EffectivePlanApiResponse>()
    }

    suspend fun fetchSubstitutionExercises(
        slug: String,
        limit: Int = 12,
        authorization: String? = null,
    ): Result<SubstitutionExercisesApiResponse> = runCatching {
        val response = client.get(base("api/mobile/exercises/substitutions")) {
            applyBearerAuthorization(authorization)
            parameter("slug", slug)
            parameter("limit", limit)
        }
        ensureSuccess(response, "Substitution request failed")
        response.body<SubstitutionExercisesApiResponse>()
    }

    suspend fun updateUserProgramCustomizations(
        userProgramId: String,
        request: UserProgramUpdateRequest,
        authorization: String? = null,
    ): Result<Unit> = runCatching {
        val response = client.put(base("api/mobile/user-programs/$userProgramId")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "User program update failed")
    }

    suspend fun login(request: LoginRequestDto): Result<AuthApiResponse<AuthDataDto>> = runCatching {
        val response = client.post(base("api/mobile/auth/login")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Login failed")
        response.body<AuthApiResponse<AuthDataDto>>()
    }

    suspend fun register(request: RegisterRequestDto): Result<AuthApiResponse<AuthDataDto>> = runCatching {
        val response = client.post(base("api/mobile/auth/register")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Registration failed")
        response.body<AuthApiResponse<AuthDataDto>>()
    }

    suspend fun googleAuth(request: GoogleAuthRequestDto): Result<AuthApiResponse<AuthDataDto>> = runCatching {
        val response = client.post(base("api/mobile/auth/google")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Google sign-in failed")
        response.body<AuthApiResponse<AuthDataDto>>()
    }

    suspend fun forgotPassword(request: ForgotPasswordRequestDto): Result<AuthApiResponse<AuthDataDto>> = runCatching {
        val response = client.post(base("api/mobile/auth/forgot-password")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Forgot password failed")
        response.body<AuthApiResponse<AuthDataDto>>()
    }

    suspend fun refresh(refreshToken: String): Result<AuthApiResponse<AuthTokensDto>> = runCatching {
        val response = client.post(base("api/mobile/auth/refresh")) {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequestDto(refreshToken))
        }
        ensureSuccess(response, "Token refresh failed")
        response.body<AuthApiResponse<AuthTokensDto>>()
    }

    suspend fun logout(
        request: LogoutRequestDto,
        authorization: String? = null,
    ): Result<AuthApiResponse<AuthDataDto>> = runCatching {
        val response = client.post(base("api/mobile/auth/logout")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Logout failed")
        response.body<AuthApiResponse<AuthDataDto>>()
    }

    suspend fun deleteAccount(authorization: String? = null): Result<AuthApiResponse<Unit>> = runCatching {
        val response = client.delete(base("api/mobile/auth/account")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Delete account failed")
        response.body<AuthApiResponse<Unit>>()
    }

    suspend fun fetchAuthProfile(authorization: String? = null): Result<AuthApiResponse<UserPublicDto>> = runCatching {
        val response = client.get(base("api/mobile/auth/profile")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Profile request failed")
        response.body<AuthApiResponse<UserPublicDto>>()
    }

    suspend fun updateAuthSettings(
        request: UpdateSettingsRequestDto,
        authorization: String? = null,
    ): Result<AuthApiResponse<UserPublicDto>> = runCatching {
        val response = client.patch(base("api/mobile/auth/settings")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Settings update failed")
        response.body<AuthApiResponse<UserPublicDto>>()
    }

    suspend fun fetchLevelProfile(authorization: String? = null): Result<LevelProfileApiResponse> = runCatching {
        val response = client.get(base("api/mobile/level-profile")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Level profile request failed")
        response.body<LevelProfileApiResponse>()
    }

    suspend fun fetchLevelProfileHistory(
        authorization: String? = null,
    ): Result<LevelProfileHistoryApiResponse> = runCatching {
        val response = client.get(base("api/mobile/level-profile/history")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Level profile history request failed")
        response.body<LevelProfileHistoryApiResponse>()
    }

    suspend fun fetchLevelDefinitions(
        authorization: String? = null,
    ): Result<LevelsListApiResponse> = runCatching {
        val response = client.get(base("api/mobile/level-profile/levels")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Level definitions request failed")
        response.body<LevelsListApiResponse>()
    }

    suspend fun fetchActivePlan(authorization: String? = null): Result<ActivePlanApiResponse> = runCatching {
        val response = client.get(base("api/mobile/plan")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Active plan request failed")
        response.body<ActivePlanApiResponse>()
    }

    suspend fun fetchTodayPlan(authorization: String? = null): Result<TodayPlanApiResponse> = runCatching {
        val response = client.get(base("api/mobile/plan/today")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Today plan request failed")
        response.body<TodayPlanApiResponse>()
    }

    suspend fun enrollProgram(
        programId: String,
        authorization: String? = null,
    ): Result<ActivePlanApiResponse> = runCatching {
        val response = client.post(base("api/mobile/plan/enroll")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(EnrollProgramRequestDto(programId))
        }
        ensureSuccess(response, "Enrollment failed")
        response.body<ActivePlanApiResponse>()
    }

    suspend fun fetchSync(
        updatedAfter: String? = null,
        forceRefresh: Boolean = false,
        authorization: String? = null,
        /** P2.2: prefer summary reports on sync; details via /mobile/reports/metrics. */
        includeReports: String = "summary",
    ): Result<MobileSyncApiResponse> = runCatching {
        val response = client.get(base("api/mobile/sync")) {
            applyBearerAuthorization(authorization)
            updatedAfter?.let { parameter("updatedAfter", it) }
            if (forceRefresh) {
                parameter("forceRefresh", true)
            }
            parameter("includeReports", includeReports)
        }
        ensureSuccess(response, "Sync request failed")
        val body = response.body<MobileSyncApiResponse>()
        if (!body.success) {
            error(body.error ?: "Sync request failed.")
        }
        body
    }

    suspend fun fetchUserPrograms(
        updatedAfter: String? = null,
        authorization: String? = null,
    ): Result<List<UserProgramExportDto>> = runCatching {
        val response = client.get(base("api/mobile/user-programs")) {
            applyBearerAuthorization(authorization)
            updatedAfter?.let { parameter("updatedAfter", it) }
        }
        ensureSuccess(response, "User programs request failed")
        val body = response.body<UserProgramsApiResponse>()
        if (!body.success) {
            error(body.error ?: "User programs request failed.")
        }
        body.userPrograms
    }

    suspend fun fetchSyncUserPrograms(
        forceRefresh: Boolean = false,
        authorization: String? = null,
        updatedAfter: String? = null,
    ): Result<List<UserProgramExportDto>> = fetchUserPrograms(
        updatedAfter = if (forceRefresh) null else updatedAfter,
        authorization = authorization,
    )

    /** Alias for sync orchestration (WS-3) — returns full payload including meta + audio manifest. */
    suspend fun fetchMobileSync(
        updatedAfter: String? = null,
        forceRefresh: Boolean = false,
        authorization: String? = null,
        includeReports: String = "summary",
    ): Result<MobileSyncApiResponse> = fetchSync(
        updatedAfter = updatedAfter,
        forceRefresh = forceRefresh,
        authorization = authorization,
        includeReports = includeReports,
    )

    suspend fun fetchWorkoutTrainingConfig(
        workoutTemplateId: String,
        authorization: String? = null,
    ): Result<TrainingConfigApiResponse> = runCatching {
        val response = client.get(base("api/mobile/workout-templates/$workoutTemplateId/training-config")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Training config request failed")
        response.body<TrainingConfigApiResponse>()
    }

    suspend fun fetchWorkoutAudioManifest(
        slug: String,
        authorization: String? = null,
    ): Result<EntityAudioManifestApiResponse> = runCatching {
        val response = client.get(base("api/mobile/workout-templates/$slug/audio-manifest")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Workout audio manifest request failed")
        response.body<EntityAudioManifestApiResponse>()
    }

    suspend fun fetchExerciseAudioManifest(
        slug: String,
        authorization: String? = null,
    ): Result<EntityAudioManifestApiResponse> = runCatching {
        val response = client.get(base("api/mobile/exercises/$slug/audio-manifest")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Exercise audio manifest request failed")
        response.body<EntityAudioManifestApiResponse>()
    }

    suspend fun startPlannedWorkout(
        workoutId: String,
        request: PlannedWorkoutStartRequestDto,
        authorization: String? = null,
    ): Result<PlannedWorkoutApiResponse> = runCatching {
        val response = client.post(base("api/mobile/planned-workouts/$workoutId/start")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Planned workout start failed")
        response.body<PlannedWorkoutApiResponse>()
    }

    suspend fun completePlannedWorkout(
        workoutId: String,
        request: PlannedWorkoutCompleteRequestDto,
        authorization: String? = null,
    ): Result<PlannedWorkoutApiResponse> = runCatching {
        val response = client.post(base("api/mobile/planned-workouts/$workoutId/complete")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Planned workout complete failed")
        response.body<PlannedWorkoutApiResponse>()
    }

    suspend fun reportPlannedWorkout(
        workoutId: String,
        request: PlannedWorkoutCompleteRequestDto,
        authorization: String? = null,
    ): Result<PlannedWorkoutApiResponse> = runCatching {
        val response = client.post(base("api/mobile/planned-workouts/$workoutId/report")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Planned workout report failed")
        response.body<PlannedWorkoutApiResponse>()
    }

    suspend fun uploadWorkoutExecution(
        request: WorkoutExecutionUploadRequestDto,
        authorization: String? = null,
    ): Result<WorkoutExecutionApiResponse> = runCatching {
        val response = client.post(base("api/mobile/workout-executions")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Workout execution upload failed")
        response.body<WorkoutExecutionApiResponse>()
    }

    suspend fun uploadExploreWorkout(
        request: ExploreWorkoutUploadRequestDto,
        authorization: String? = null,
    ): Result<ExploreWorkoutApiResponse> = runCatching {
        val response = client.post(base("api/mobile/workout-executions/explore")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Explore workout upload failed")
        response.body<ExploreWorkoutApiResponse>()
    }

    suspend fun fetchUpcomingReassessments(authorization: String? = null): Result<ReassessmentListApiResponse> = runCatching {
        val response = client.get(base("api/mobile/reassessment/upcoming")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Reassessment request failed")
        response.body<ReassessmentListApiResponse>()
    }

    suspend fun resolveAssessmentTemplate(
        mode: String = "initial",
        authorization: String? = null,
    ): Result<AssessmentTemplateApiResponse> = runCatching {
        val response = client.get(base("api/mobile/assessment-templates/resolve")) {
            applyBearerAuthorization(authorization)
            parameter("mode", mode)
        }
        ensureSuccess(response, "Assessment template request failed")
        response.body<AssessmentTemplateApiResponse>()
    }

    suspend fun uploadAssessment(
        request: BodyScanUploadRequestDto,
        authorization: String? = null,
    ): Result<AssessmentApiResponse> = runCatching {
        val response = client.post(base("api/assessment")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Assessment upload failed")
        response.body<AssessmentApiResponse>()
    }

    suspend fun fetchLatestAssessment(
        authorization: String? = null,
    ): Result<AssessmentApiResponse> = runCatching {
        val response = client.get(base("api/assessment/latest")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Latest assessment request failed")
        response.body<AssessmentApiResponse>()
    }

    suspend fun fetchAssessmentProgress(
        authorization: String? = null,
    ): Result<AssessmentProgressApiResponse> = runCatching {
        val response = client.get(base("api/assessment/progress")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Assessment progress request failed")
        response.body<AssessmentProgressApiResponse>()
    }

    suspend fun fetchTrainingProfile(
        authorization: String? = null,
    ): Result<TrainingProfileApiResponse> = runCatching {
        val response = client.get(base("api/mobile/training-profile")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Training profile request failed")
        response.body<TrainingProfileApiResponse>()
    }

    suspend fun putTrainingProfile(
        request: TrainingProfilePutRequest,
        authorization: String? = null,
    ): Result<TrainingProfileApiResponse> = runCatching {
        val response = client.put(base("api/mobile/training-profile")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Training profile update failed")
        response.body<TrainingProfileApiResponse>()
    }

    suspend fun completePlan(
        authorization: String? = null,
        request: PlanCompleteRequestDto = PlanCompleteRequestDto(),
    ): Result<PlanMutationResponse> = runCatching {
        val response = client.post(base("api/mobile/plan/complete")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Plan complete failed")
        response.body<PlanMutationResponse>()
    }

    suspend fun upsertExercisePreference(
        exerciseId: String,
        request: UserExercisePreferenceUpsertRequest,
        authorization: String? = null,
    ): Result<Unit> = runCatching {
        val response = client.put(base("api/mobile/exercise-preferences/$exerciseId")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Exercise preference upsert failed")
    }

    suspend fun deleteExercisePreference(
        exerciseId: String,
        authorization: String? = null,
    ): Result<Unit> = runCatching {
        val response = client.delete(base("api/mobile/exercise-preferences/$exerciseId")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Exercise preference delete failed")
    }

    suspend fun createUserProgramOverride(
        userProgramId: String,
        request: UserProgramOverrideCreateRequest,
        authorization: String? = null,
    ): Result<UserProgramOverrideCreateResponse> = runCatching {
        val response = client.post(base("api/mobile/user-programs/$userProgramId/overrides")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "User program override create failed")
        response.body<UserProgramOverrideCreateResponse>()
    }

    suspend fun deleteUserProgramOverride(
        userProgramId: String,
        overrideId: String,
        authorization: String? = null,
    ): Result<Unit> = runCatching {
        val response = client.delete(
            base("api/mobile/user-programs/$userProgramId/overrides/$overrideId"),
        ) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "User program override delete failed")
    }

    suspend fun fetchProgressionHistory(
        authorization: String? = null,
    ): Result<ProgressionHistoryApiResponse> = runCatching {
        val response = client.get(base("api/mobile/progression/history")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Progression history request failed")
        response.body<ProgressionHistoryApiResponse>()
    }

    suspend fun fetchRecentProgression(
        authorization: String? = null,
    ): Result<ProgressionHistoryApiResponse> = runCatching {
        val response = client.get(base("api/mobile/progression/recent")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Recent progression request failed")
        response.body<ProgressionHistoryApiResponse>()
    }

    suspend fun fetchSessionProgression(
        sessionId: String,
        authorization: String? = null,
    ): Result<ProgressionHistoryApiResponse> = runCatching {
        val response = client.get(base("api/mobile/progression/session/$sessionId")) {
            applyBearerAuthorization(authorization)
        }
        ensureSuccess(response, "Session progression request failed")
        response.body<ProgressionHistoryApiResponse>()
    }

    suspend fun markProgressionSeen(
        request: ProgressionMarkSeenRequest,
        authorization: String? = null,
    ): Result<ProgressionMarkSeenResponse> = runCatching {
        val response = client.post(base("api/mobile/progression/mark-seen")) {
            applyBearerAuthorization(authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        ensureSuccess(response, "Progression mark-seen failed")
        response.body<ProgressionMarkSeenResponse>()
    }

    private suspend fun ensureSuccess(response: HttpResponse, label: String) {
        if (!response.status.isSuccess()) {
            val body = runCatching { response.bodyAsText() }.getOrNull()
            throw MovitApiException(
                status = response.status.value,
                body = body,
                message = "$label (${response.status.value})",
            )
        }
    }
}