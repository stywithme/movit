package com.trainingvalidator.poc.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST

/**
 * Auth API Interface
 *
 * Retrofit interface for auth endpoints.
 */
interface AuthApi {

    @POST("api/mobile/auth/register")
    suspend fun register(
        @Body request: RegisterRequest
    ): Response<AuthApiResponse<AuthData>>

    @POST("api/mobile/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<AuthApiResponse<AuthData>>

    @POST("api/mobile/auth/google")
    suspend fun googleAuth(
        @Body request: GoogleAuthRequest
    ): Response<AuthApiResponse<AuthData>>

    @POST("api/mobile/auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): Response<AuthApiResponse<AuthTokens>>

    @POST("api/mobile/auth/logout")
    suspend fun logout(
        @Body request: LogoutRequest
    ): Response<AuthApiResponse<Unit>>

    @POST("api/mobile/auth/forgot-password")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
    ): Response<AuthApiResponse<Unit>>

    @POST("api/mobile/auth/reset-password")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest
    ): Response<AuthApiResponse<Unit>>

    @GET("api/mobile/auth/profile")
    suspend fun getProfile(
        @Header("Authorization") authHeader: String
    ): Response<AuthApiResponse<UserPublic>>

    @PATCH("api/mobile/auth/profile")
    suspend fun updateProfile(
        @Header("Authorization") authHeader: String,
        @Body request: UpdateProfileRequest
    ): Response<AuthApiResponse<UserPublic>>

    @PATCH("api/mobile/auth/settings")
    suspend fun updateSettings(
        @Header("Authorization") authHeader: String,
        @Body request: UpdateSettingsRequest
    ): Response<AuthApiResponse<UserPublic>>
}
