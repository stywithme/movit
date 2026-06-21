package com.movit.core.data.repository

import com.movit.core.data.MovitData
import com.movit.core.data.platform.AuthSessionSnapshot
import com.movit.core.data.platform.MovitPlatformBindings
import com.movit.core.data.platform.toSnapshot
import com.movit.core.network.MovitMobileApi
import com.movit.core.network.dto.ForgotPasswordRequestDto
import com.movit.core.network.dto.GoogleAuthRequestDto
import com.movit.core.network.dto.LoginRequestDto
import com.movit.core.network.dto.LogoutRequestDto
import com.movit.core.network.dto.RegisterRequestDto
import com.movit.core.network.dto.TrainingProfilePayloadDto
import com.movit.core.network.dto.TrainingProfilePutRequest
import com.movit.core.network.dto.UpdateSettingsRequestDto
import com.movit.core.network.dto.ActivePlanDto
import com.movit.core.network.dto.AssessmentProgressDto
import com.movit.core.network.dto.AssessmentTemplateDto
import com.movit.core.network.dto.BodyScanResultDto
import com.movit.core.network.dto.BodyScanUploadRequestDto
import com.movit.core.network.dto.LevelProfileDetailDto
import com.movit.core.network.dto.ReassessmentDto
import com.movit.core.network.dto.UserPublicDto
import com.movit.shared.AppResult

class AccountSyncRepository(
    private val api: MovitMobileApi,
    private val platform: () -> MovitPlatformBindings,
) {
    suspend fun login(email: String, password: String): AppResult<AuthSessionSnapshot> {
        val response = api.login(LoginRequestDto(email, password)).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Login failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: response.message ?: "Login failed.")
        }
        val data = response.data ?: return AppResult.Failure("Login response was empty.")
        val snapshot = data.toSnapshot()
        platform().persistAuthSession(snapshot)
        return AppResult.Success(snapshot)
    }

    suspend fun googleAuth(
        idToken: String,
        googleId: String,
        email: String,
        name: String,
        avatarUrl: String? = null,
    ): AppResult<AuthSessionSnapshot> {
        val response = api.googleAuth(
            GoogleAuthRequestDto(
                idToken = idToken,
                googleId = googleId,
                email = email,
                name = name,
                avatarUrl = avatarUrl,
            ),
        ).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Google sign-in failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: response.message ?: "Google sign-in failed.")
        }
        val data = response.data ?: return AppResult.Failure("Google sign-in response was empty.")
        val snapshot = data.toSnapshot()
        platform().persistAuthSession(snapshot)
        return AppResult.Success(snapshot)
    }

    suspend fun register(name: String, email: String, password: String): AppResult<AuthSessionSnapshot> {
        val response = api.register(RegisterRequestDto(name, email, password)).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Registration failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: response.message ?: "Registration failed.")
        }
        val data = response.data ?: return AppResult.Failure("Registration response was empty.")
        val snapshot = data.toSnapshot()
        platform().persistAuthSession(snapshot)
        platform().setOnboardingCompleted(false)
        return AppResult.Success(snapshot)
    }

    suspend fun forgotPassword(email: String): AppResult<Unit> {
        val response = api.forgotPassword(ForgotPasswordRequestDto(email)).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Request failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: response.message ?: "Request failed.")
        }
        return AppResult.Success(Unit)
    }

    suspend fun logout(): AppResult<Unit> {
        val bindings = platform()
        val refresh = bindings.refreshToken()
        val auth = bindings.authHeader()
        if (auth != null && refresh != null) {
            api.logout(LogoutRequestDto(refresh), authorization = auth)
        }
        clearLocalSession(bindings)
        return AppResult.Success(Unit)
    }

    suspend fun deleteAccount(): AppResult<Unit> {
        val bindings = platform()
        val auth = bindings.authHeader()
            ?: return AppResult.Failure("Sign in to delete your account.")
        val response = api.deleteAccount(authorization = auth).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Delete account failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: response.message ?: "Delete account failed.")
        }
        clearLocalSession(bindings)
        return AppResult.Success(Unit)
    }

    private suspend fun clearLocalSession(bindings: MovitPlatformBindings) {
        if (MovitData.isInstalled) {
            MovitData.clearAllUserData()
        }
        bindings.clearAuthSession()
    }

    suspend fun fetchProfile(): AppResult<UserPublicDto> {
        val auth = platform().authHeader()
            ?: return AppResult.Failure("Sign in to load your profile.")
        val response = api.fetchAuthProfile(auth).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Profile request failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: "Profile request failed.")
        }
        val user = response.data ?: return AppResult.Failure("Profile response was empty.")
        val bindings = platform()
        val refresh = bindings.refreshToken().orEmpty()
        bindings.persistAuthSession(
            user.toSnapshot(
                accessToken = bindings.readAccessTokenRaw()
                    ?: auth.removePrefix("Bearer ").trim(),
                refreshToken = refresh,
                expiresInSeconds = 0,
            ),
        )
        return AppResult.Success(user)
    }

    suspend fun updateSettings(
        preferredLanguage: String? = null,
        voiceFeedback: Boolean? = null,
        notifications: Boolean? = null,
    ): AppResult<UserPublicDto> {
        val auth = platform().authHeader()
            ?: return AppResult.Failure("Sign in to update settings.")
        val response = api.updateAuthSettings(
            UpdateSettingsRequestDto(
                preferredLanguage = preferredLanguage,
                voiceFeedback = voiceFeedback,
                notifications = notifications,
            ),
            authorization = auth,
        ).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Settings update failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: "Settings update failed.")
        }
        val user = response.data ?: return AppResult.Failure("Settings response was empty.")
        platform().updateUserSettings(
            preferredLanguage = preferredLanguage,
            voiceFeedback = voiceFeedback,
            notifications = notifications,
        )
        return AppResult.Success(user)
    }

    suspend fun fetchTrainingProfile(): AppResult<TrainingProfilePayloadDto> {
        val auth = platform().authHeader()
            ?: return AppResult.Failure("Sign in to load your training profile.")
        val response = api.fetchTrainingProfile(authorization = auth).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Training profile request failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: "Training profile request failed.")
        }
        return AppResult.Success(response.data ?: TrainingProfilePayloadDto())
    }

    suspend fun putTrainingProfile(request: TrainingProfilePutRequest): AppResult<Unit> {
        val auth = platform().authHeader()
            ?: return AppResult.Failure("Sign in to save your training profile.")
        val response = api.putTrainingProfile(request, authorization = auth).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Training profile save failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: "Training profile save failed.")
        }
        platform().setOnboardingCompleted(true)
        return AppResult.Success(Unit)
    }

    suspend fun fetchLevelProfile(): AppResult<LevelProfileDetailDto> {
        val auth = platform().authHeader()
            ?: return AppResult.Failure("Sign in to load your level profile.")
        val response = api.fetchLevelProfile(auth).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Level profile request failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: "Level profile request failed.")
        }
        val data = response.data ?: return AppResult.Failure("Level profile response was empty.")
        return AppResult.Success(data)
    }

    suspend fun fetchActivePlan(): AppResult<ActivePlanDto> {
        val auth = platform().authHeader()
            ?: return AppResult.Failure("Sign in to load your training plan.")
        val response = api.fetchActivePlan(auth).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Active plan request failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: "Active plan request failed.")
        }
        val data = response.data ?: return AppResult.Failure("Active plan response was empty.")
        return AppResult.Success(data)
    }

    suspend fun fetchUpcomingReassessments(): AppResult<List<ReassessmentDto>> {
        val auth = platform().authHeader()
            ?: return AppResult.Failure("Sign in to load reassessments.")
        val response = api.fetchUpcomingReassessments(auth).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Reassessment request failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: "Reassessment request failed.")
        }
        return AppResult.Success(response.data.orEmpty())
    }

    suspend fun resolveAssessmentTemplate(mode: String = "initial"): AppResult<AssessmentTemplateDto> {
        val auth = platform().authHeader()
            ?: return AppResult.Failure("Sign in to start your assessment.")
        val response = api.resolveAssessmentTemplate(mode = mode, authorization = auth).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Assessment template request failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: "Assessment template request failed.")
        }
        val data = response.data ?: return AppResult.Failure("Assessment template response was empty.")
        return AppResult.Success(data)
    }

    suspend fun uploadAssessment(request: BodyScanUploadRequestDto): AppResult<BodyScanResultDto> {
        val auth = platform().authHeader()
            ?: return AppResult.Failure("Sign in to save your assessment.")
        val response = api.uploadAssessment(request, authorization = auth).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Assessment upload failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: response.message ?: "Assessment upload failed.")
        }
        val data = response.data ?: return AppResult.Failure("Assessment response was empty.")
        return AppResult.Success(data)
    }

    suspend fun fetchLatestAssessment(): AppResult<BodyScanResultDto> {
        val auth = platform().authHeader()
            ?: return AppResult.Failure("Sign in to load your assessment.")
        val response = api.fetchLatestAssessment(auth).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Latest assessment request failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: "Latest assessment request failed.")
        }
        val data = response.data ?: return AppResult.Failure("Latest assessment response was empty.")
        return AppResult.Success(data)
    }

    suspend fun fetchAssessmentProgress(): AppResult<AssessmentProgressDto> {
        val auth = platform().authHeader()
            ?: return AppResult.Failure("Sign in to load assessment progress.")
        val response = api.fetchAssessmentProgress(auth).getOrElse { error ->
            return AppResult.Failure(error.message ?: "Assessment progress request failed.")
        }
        if (!response.success) {
            return AppResult.Failure(response.error ?: "Assessment progress request failed.")
        }
        val data = response.data ?: return AppResult.Failure("Assessment progress response was empty.")
        return AppResult.Success(data)
    }
}
