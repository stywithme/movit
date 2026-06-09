package com.movit.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AuthApiResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: String? = null,
    val message: String? = null,
)

@Serializable
data class AuthDataDto(
    val user: UserPublicDto = UserPublicDto(),
    val tokens: AuthTokensDto = AuthTokensDto(),
)

@Serializable
data class UserPublicDto(
    val id: String = "",
    val email: String = "",
    val name: String = "",
    val avatarUrl: String? = null,
    val provider: String = "",
    val preferredLanguage: String = "en",
    val voiceFeedback: Boolean = true,
    val notifications: Boolean = true,
    val isPro: Boolean = false,
    val subscriptionExpiry: String? = null,
    val totalWorkoutExecutions: Int = 0,
    val totalMinutes: Int = 0,
    val emailVerified: Boolean = false,
    val createdAt: String = "",
)

@Serializable
data class AuthTokensDto(
    val accessToken: String = "",
    val refreshToken: String = "",
    val expiresIn: Int = 0,
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterRequestDto(
    val name: String,
    val email: String,
    val password: String,
)

@Serializable
data class ForgotPasswordRequestDto(
    val email: String,
)

@Serializable
data class LogoutRequestDto(
    val refreshToken: String,
)

@Serializable
data class UpdateSettingsRequestDto(
    val preferredLanguage: String? = null,
    val voiceFeedback: Boolean? = null,
    val notifications: Boolean? = null,
)
