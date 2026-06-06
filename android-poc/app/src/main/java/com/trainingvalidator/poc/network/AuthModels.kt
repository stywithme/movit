package com.trainingvalidator.poc.network

/**
 * Auth API Models
 *
 * These models match the backend auth response structure.
 */

data class AuthApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val message: String? = null,
    val details: Map<String, List<String>>? = null
)

data class AuthData(
    val user: UserPublic,
    val tokens: AuthTokens
)

data class UserPublic(
    val id: String,
    val email: String,
    val name: String,
    val avatarUrl: String? = null,
    val provider: String,
    val preferredLanguage: String,
    val voiceFeedback: Boolean,
    val notifications: Boolean,
    val isPro: Boolean,
    val subscriptionExpiry: String? = null,
    val totalWorkoutExecutions: Int,
    val totalMinutes: Int,
    val emailVerified: Boolean,
    val createdAt: String
)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int
)

// ==================== Requests ====================

data class RegisterRequest(
    val name: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class LogoutRequest(
    val refreshToken: String
)

data class ForgotPasswordRequest(
    val email: String
)

data class ResetPasswordRequest(
    val token: String,
    val password: String
)

data class UpdateProfileRequest(
    val name: String? = null,
    val avatarUrl: String? = null
)

data class UpdateSettingsRequest(
    val preferredLanguage: String? = null,
    val voiceFeedback: Boolean? = null,
    val notifications: Boolean? = null
)

data class GoogleAuthRequest(
    val idToken: String,
    val googleId: String,
    val email: String,
    val name: String,
    val avatarUrl: String? = null
)
