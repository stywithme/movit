package com.movit.core.data.platform

import com.movit.core.network.dto.AuthDataDto
import com.movit.core.network.dto.AuthTokensDto
import com.movit.core.network.dto.UserPublicDto

data class AuthSessionSnapshot(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Int,
    val userId: String,
    val name: String,
    val email: String,
    val avatarUrl: String?,
    val preferredLanguage: String,
    val voiceFeedback: Boolean,
    val notifications: Boolean,
    val isPro: Boolean,
    val subscriptionExpiry: String?,
    val totalWorkouts: Int,
    val totalMinutes: Int,
)

fun AuthDataDto.toSnapshot(): AuthSessionSnapshot = AuthSessionSnapshot(
    accessToken = tokens.accessToken,
    refreshToken = tokens.refreshToken,
    expiresInSeconds = tokens.expiresIn,
    userId = user.id,
    name = user.name,
    email = user.email,
    avatarUrl = user.avatarUrl,
    preferredLanguage = user.preferredLanguage,
    voiceFeedback = user.voiceFeedback,
    notifications = user.notifications,
    isPro = user.isPro,
    subscriptionExpiry = user.subscriptionExpiry,
    totalWorkouts = user.totalWorkoutExecutions,
    totalMinutes = user.totalMinutes,
)

fun AuthSessionSnapshot.toAuthDataDto(): AuthDataDto = AuthDataDto(
    user = UserPublicDto(
        id = userId,
        email = email,
        name = name,
        avatarUrl = avatarUrl,
        provider = "email",
        preferredLanguage = preferredLanguage,
        voiceFeedback = voiceFeedback,
        notifications = notifications,
        isPro = isPro,
        subscriptionExpiry = subscriptionExpiry,
        totalWorkoutExecutions = totalWorkouts,
        totalMinutes = totalMinutes,
        emailVerified = true,
        createdAt = "",
    ),
    tokens = AuthTokensDto(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresIn = expiresInSeconds,
    ),
)

fun UserPublicDto.toSnapshot(
    accessToken: String,
    refreshToken: String,
    expiresInSeconds: Int,
): AuthSessionSnapshot = AuthSessionSnapshot(
    accessToken = accessToken,
    refreshToken = refreshToken,
    expiresInSeconds = expiresInSeconds,
    userId = id,
    name = name,
    email = email,
    avatarUrl = avatarUrl,
    preferredLanguage = preferredLanguage,
    voiceFeedback = voiceFeedback,
    notifications = notifications,
    isPro = isPro,
    subscriptionExpiry = subscriptionExpiry,
    totalWorkouts = totalWorkoutExecutions,
    totalMinutes = totalMinutes,
)
