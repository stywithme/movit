package com.movit.core.network

import com.movit.core.network.dto.AuthApiResponse
import com.movit.core.network.dto.AuthDataDto
import com.movit.core.network.dto.AuthTokensDto
import com.movit.core.network.dto.ExploreApiResponse
import com.movit.core.network.dto.ForgotPasswordRequestDto
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.EffectivePlanApiResponse
import com.movit.core.network.dto.ActivePlanApiResponse
import com.movit.core.network.dto.EnrollProgramRequestDto
import com.movit.core.network.dto.MobileSyncApiResponse
import com.movit.core.network.dto.LevelProfileApiResponse
import com.movit.core.network.dto.ReassessmentListApiResponse
import com.movit.core.network.dto.LoginRequestDto
import com.movit.core.network.dto.LogoutRequestDto
import com.movit.core.network.dto.MetricsApiResponse
import com.movit.core.network.dto.RefreshTokenRequestDto
import com.movit.core.network.dto.RegisterRequestDto
import com.movit.core.network.dto.ReportsDashboardApiResponse
import com.movit.core.network.dto.SubstitutionExercisesApiResponse
import com.movit.core.network.dto.TrainingProfileApiResponse
import com.movit.core.network.dto.TrainingProfilePutRequest
import com.movit.core.network.dto.UpdateSettingsRequestDto
import com.movit.core.network.dto.UserProgramExportDto
import com.movit.core.network.dto.UserProgramUpdateRequest
import com.movit.core.network.dto.UserPublicDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
        if (!response.status.isSuccess()) {
            error("Explore request failed (${response.status.value})")
        }
        response.body<ExploreApiResponse>()
    }

    suspend fun fetchHome(authorization: String? = null): Result<HomeApiResponse> = runCatching {
        val response = client.get(base("api/mobile/home")) {
            applyBearerAuthorization(authorization)
        }
        if (!response.status.isSuccess()) {
            error("Home request failed (${response.status.value})")
        }
        response.body<HomeApiResponse>()
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
        if (!response.status.isSuccess()) {
            error("Reports dashboard request failed (${response.status.value})")
        }
        response.body<ReportsDashboardApiResponse>()
    }

    suspend fun fetchExerciseMetrics(
        programId: String,
        exerciseSlug: String,
        authorization: String? = null,
    ): Result<MetricsApiResponse> = runCatching {
        val response = client.get(base("api/mobile/reports/metrics")) {
            applyBearerAuthorization(authorization)
            parameter("programId", programId)
            parameter("scope", "exercise")
            parameter("exerciseSlug", exerciseSlug)
            parameter("includeHistory", true)
        }
        if (!response.status.isSuccess()) {
            error("Exercise metrics request failed (${response.status.value})")
        }
        response.body<MetricsApiResponse>()
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
        if (!response.status.isSuccess()) {
            error("Effective plan request failed (${response.status.value})")
        }
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
        if (!response.status.isSuccess()) {
            error("Substitution request failed (${response.status.value})")
        }
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
        if (!response.status.isSuccess()) {
            error("User program update failed (${response.status.value})")
        }
    }

    suspend fun login(request: LoginRequestDto): Result<AuthApiResponse<AuthDataDto>> = runCatching {
        val response = client.post(base("api/mobile/auth/login")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            error("Login failed (${response.status.value})")
        }
        response.body<AuthApiResponse<AuthDataDto>>()
    }

    suspend fun register(request: RegisterRequestDto): Result<AuthApiResponse<AuthDataDto>> = runCatching {
        val response = client.post(base("api/mobile/auth/register")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            error("Registration failed (${response.status.value})")
        }
        response.body<AuthApiResponse<AuthDataDto>>()
    }

    suspend fun forgotPassword(request: ForgotPasswordRequestDto): Result<AuthApiResponse<AuthDataDto>> = runCatching {
        val response = client.post(base("api/mobile/auth/forgot-password")) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            error("Forgot password failed (${response.status.value})")
        }
        response.body<AuthApiResponse<AuthDataDto>>()
    }

    suspend fun refresh(refreshToken: String): Result<AuthApiResponse<AuthTokensDto>> = runCatching {
        val response = client.post(base("api/mobile/auth/refresh")) {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequestDto(refreshToken))
        }
        if (!response.status.isSuccess()) {
            error("Token refresh failed (${response.status.value})")
        }
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
        if (!response.status.isSuccess()) {
            error("Logout failed (${response.status.value})")
        }
        response.body<AuthApiResponse<AuthDataDto>>()
    }

    suspend fun fetchAuthProfile(authorization: String? = null): Result<AuthApiResponse<UserPublicDto>> = runCatching {
        val response = client.get(base("api/mobile/auth/profile")) {
            applyBearerAuthorization(authorization)
        }
        if (!response.status.isSuccess()) {
            error("Profile request failed (${response.status.value})")
        }
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
        if (!response.status.isSuccess()) {
            error("Settings update failed (${response.status.value})")
        }
        response.body<AuthApiResponse<UserPublicDto>>()
    }

    suspend fun fetchLevelProfile(authorization: String? = null): Result<LevelProfileApiResponse> = runCatching {
        val response = client.get(base("api/mobile/level-profile")) {
            applyBearerAuthorization(authorization)
        }
        if (!response.status.isSuccess()) {
            error("Level profile request failed (${response.status.value})")
        }
        response.body<LevelProfileApiResponse>()
    }

    suspend fun fetchActivePlan(authorization: String? = null): Result<ActivePlanApiResponse> = runCatching {
        val response = client.get(base("api/mobile/plan")) {
            applyBearerAuthorization(authorization)
        }
        if (!response.status.isSuccess()) {
            error("Active plan request failed (${response.status.value})")
        }
        response.body<ActivePlanApiResponse>()
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
        if (!response.status.isSuccess()) {
            error("Enrollment failed (${response.status.value})")
        }
        response.body<ActivePlanApiResponse>()
    }

    suspend fun fetchSyncUserPrograms(
        forceRefresh: Boolean = false,
        authorization: String? = null,
    ): Result<List<UserProgramExportDto>> = runCatching {
        val response = client.get(base("api/mobile/sync")) {
            applyBearerAuthorization(authorization)
            if (forceRefresh) {
                parameter("forceRefresh", true)
            }
        }
        if (!response.status.isSuccess()) {
            error("Sync request failed (${response.status.value})")
        }
        val body = response.body<MobileSyncApiResponse>()
        if (!body.success) {
            error(body.error ?: "Sync request failed.")
        }
        body.data?.userPrograms.orEmpty()
    }

    suspend fun fetchUpcomingReassessments(authorization: String? = null): Result<ReassessmentListApiResponse> = runCatching {
        val response = client.get(base("api/mobile/reassessment/upcoming")) {
            applyBearerAuthorization(authorization)
        }
        if (!response.status.isSuccess()) {
            error("Reassessment request failed (${response.status.value})")
        }
        response.body<ReassessmentListApiResponse>()
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
        if (!response.status.isSuccess()) {
            error("Training profile update failed (${response.status.value})")
        }
        response.body<TrainingProfileApiResponse>()
    }
}
