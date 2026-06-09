package com.movit.core.network

import com.movit.core.network.dto.AuthApiResponse
import com.movit.core.network.dto.AuthDataDto
import com.movit.core.network.dto.ExploreApiResponse
import com.movit.core.network.dto.ForgotPasswordRequestDto
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.EffectivePlanApiResponse
import com.movit.core.network.dto.ActivePlanApiResponse
import com.movit.core.network.dto.LevelProfileApiResponse
import com.movit.core.network.dto.ReassessmentListApiResponse
import com.movit.core.network.dto.LoginRequestDto
import com.movit.core.network.dto.LogoutRequestDto
import com.movit.core.network.dto.MetricsApiResponse
import com.movit.core.network.dto.RegisterRequestDto
import com.movit.core.network.dto.ReportsDashboardApiResponse
import com.movit.core.network.dto.SubstitutionExercisesApiResponse
import com.movit.core.network.dto.TrainingProfileApiResponse
import com.movit.core.network.dto.TrainingProfilePutRequest
import com.movit.core.network.dto.UpdateSettingsRequestDto
import com.movit.core.network.dto.UserProgramUpdateRequest
import com.movit.core.network.dto.UserPublicDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
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

    suspend fun fetchHome(authorization: String): Result<HomeApiResponse> = runCatching {
        val response = client.get(base("api/mobile/home")) {
            header("Authorization", authorization)
        }
        if (!response.status.isSuccess()) {
            error("Home request failed (${response.status.value})")
        }
        response.body<HomeApiResponse>()
    }

    suspend fun fetchReportsDashboard(
        authorization: String,
        programId: String?,
        period: String,
        source: String,
    ): Result<ReportsDashboardApiResponse> = runCatching {
        val response = client.get(base("api/mobile/reports/dashboard")) {
            header("Authorization", authorization)
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
        authorization: String,
        programId: String,
        exerciseSlug: String,
    ): Result<MetricsApiResponse> = runCatching {
        val response = client.get(base("api/mobile/reports/metrics")) {
            header("Authorization", authorization)
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
        authorization: String,
        userProgramId: String,
        weekNumber: Int,
        dayNumber: Int,
    ): Result<EffectivePlanApiResponse> = runCatching {
        val response = client.get(base("api/mobile/user-programs/$userProgramId/effective-plan")) {
            header("Authorization", authorization)
            parameter("week", weekNumber)
            parameter("day", dayNumber)
        }
        if (!response.status.isSuccess()) {
            error("Effective plan request failed (${response.status.value})")
        }
        response.body<EffectivePlanApiResponse>()
    }

    suspend fun fetchSubstitutionExercises(
        authorization: String,
        slug: String,
        limit: Int = 12,
    ): Result<SubstitutionExercisesApiResponse> = runCatching {
        val response = client.get(base("api/mobile/exercises/substitutions")) {
            header("Authorization", authorization)
            parameter("slug", slug)
            parameter("limit", limit)
        }
        if (!response.status.isSuccess()) {
            error("Substitution request failed (${response.status.value})")
        }
        response.body<SubstitutionExercisesApiResponse>()
    }

    suspend fun updateUserProgramCustomizations(
        authorization: String,
        userProgramId: String,
        request: UserProgramUpdateRequest,
    ): Result<Unit> = runCatching {
        val response = client.put(base("api/mobile/user-programs/$userProgramId")) {
            header("Authorization", authorization)
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

    suspend fun logout(authorization: String, request: LogoutRequestDto): Result<AuthApiResponse<AuthDataDto>> = runCatching {
        val response = client.post(base("api/mobile/auth/logout")) {
            header("Authorization", authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            error("Logout failed (${response.status.value})")
        }
        response.body<AuthApiResponse<AuthDataDto>>()
    }

    suspend fun fetchAuthProfile(authorization: String): Result<AuthApiResponse<UserPublicDto>> = runCatching {
        val response = client.get(base("api/mobile/auth/profile")) {
            header("Authorization", authorization)
        }
        if (!response.status.isSuccess()) {
            error("Profile request failed (${response.status.value})")
        }
        response.body<AuthApiResponse<UserPublicDto>>()
    }

    suspend fun updateAuthSettings(
        authorization: String,
        request: UpdateSettingsRequestDto,
    ): Result<AuthApiResponse<UserPublicDto>> = runCatching {
        val response = client.patch(base("api/mobile/auth/settings")) {
            header("Authorization", authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            error("Settings update failed (${response.status.value})")
        }
        response.body<AuthApiResponse<UserPublicDto>>()
    }

    suspend fun fetchLevelProfile(authorization: String): Result<LevelProfileApiResponse> = runCatching {
        val response = client.get(base("api/mobile/level-profile")) {
            header("Authorization", authorization)
        }
        if (!response.status.isSuccess()) {
            error("Level profile request failed (${response.status.value})")
        }
        response.body<LevelProfileApiResponse>()
    }

    suspend fun fetchActivePlan(authorization: String): Result<ActivePlanApiResponse> = runCatching {
        val response = client.get(base("api/mobile/plan")) {
            header("Authorization", authorization)
        }
        if (!response.status.isSuccess()) {
            error("Active plan request failed (${response.status.value})")
        }
        response.body<ActivePlanApiResponse>()
    }

    suspend fun fetchUpcomingReassessments(authorization: String): Result<ReassessmentListApiResponse> = runCatching {
        val response = client.get(base("api/mobile/reassessment/upcoming")) {
            header("Authorization", authorization)
        }
        if (!response.status.isSuccess()) {
            error("Reassessment request failed (${response.status.value})")
        }
        response.body<ReassessmentListApiResponse>()
    }

    suspend fun putTrainingProfile(
        authorization: String,
        request: TrainingProfilePutRequest,
    ): Result<TrainingProfileApiResponse> = runCatching {
        val response = client.put(base("api/mobile/training-profile")) {
            header("Authorization", authorization)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        if (!response.status.isSuccess()) {
            error("Training profile update failed (${response.status.value})")
        }
        response.body<TrainingProfileApiResponse>()
    }
}
