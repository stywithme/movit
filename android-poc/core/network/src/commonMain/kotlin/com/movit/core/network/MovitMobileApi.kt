package com.movit.core.network

import com.movit.core.network.dto.ExploreApiResponse
import com.movit.core.network.dto.HomeApiResponse
import com.movit.core.network.dto.EffectivePlanApiResponse
import com.movit.core.network.dto.MetricsApiResponse
import com.movit.core.network.dto.ReportsDashboardApiResponse
import com.movit.core.network.dto.SubstitutionExercisesApiResponse
import com.movit.core.network.dto.UserProgramUpdateRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
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
}
