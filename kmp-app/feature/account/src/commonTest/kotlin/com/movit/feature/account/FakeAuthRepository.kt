package com.movit.feature.account

import com.movit.shared.AppResult

open class FakeAuthRepository(
    private val demoEmail: String = "demo@movit.app",
    private val demoPassword: String = "demo1234",
) : AuthRepository {

    override suspend fun login(email: String, password: String): AppResult<AuthSessionUi> {
        if (email.trim().equals(demoEmail, ignoreCase = true) && password == demoPassword) {
            return AppResult.Success(
                AuthSessionUi(
                    userId = "demo-user",
                    name = "Demo Athlete",
                    email = demoEmail,
                ),
            )
        }
        return AppResult.Failure("Invalid email or password.")
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): AppResult<AuthSessionUi> {
        if (name.isBlank() || email.isBlank() || password.length < 8) {
            return AppResult.Failure("Please complete all fields with a valid password.")
        }
        return AppResult.Success(
            AuthSessionUi(
                userId = "new-user",
                name = name.trim(),
                email = email.trim(),
            ),
        )
    }

    override suspend fun googleSignIn(credentials: GoogleSignInCredentials): AppResult<AuthSessionUi> {
        return AppResult.Success(
            AuthSessionUi(
                userId = "google-user",
                name = credentials.name,
                email = credentials.email,
            ),
        )
    }

    override suspend fun forgotPassword(email: String): AppResult<Unit> {
        return if (email.isBlank()) {
            AppResult.Failure("Enter your email address.")
        } else {
            AppResult.Success(Unit)
        }
    }
}
