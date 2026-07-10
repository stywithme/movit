package com.movit.feature.account

import com.movit.shared.AppResult

data class AuthSessionUi(
    val userId: String,
    val name: String,
    val email: String,
    /** UX.7 — guest outbox rows waiting for attribution after this auth. */
    val guestOutboxCount: Int? = null,
)

interface AuthRepository {
    suspend fun login(email: String, password: String): AppResult<AuthSessionUi>
    suspend fun register(name: String, email: String, password: String): AppResult<AuthSessionUi>
    suspend fun googleSignIn(credentials: GoogleSignInCredentials): AppResult<AuthSessionUi>
    suspend fun forgotPassword(email: String): AppResult<Unit>
}
