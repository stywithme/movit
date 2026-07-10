package com.movit.feature.account

import com.movit.core.data.MovitData
import com.movit.core.data.repository.AuthenticatedSessionResult
import com.movit.shared.AppResult

class SharedAuthRepository : AuthRepository {

    override suspend fun login(email: String, password: String): AppResult<AuthSessionUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        return when (val result = MovitData.account.login(email, password)) {
            is AppResult.Success -> AppResult.Success(result.value.toUi())
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }

    override suspend fun register(
        name: String,
        email: String,
        password: String,
    ): AppResult<AuthSessionUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        return when (val result = MovitData.account.register(name, email, password)) {
            is AppResult.Success -> AppResult.Success(result.value.toUi())
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }

    override suspend fun googleSignIn(credentials: GoogleSignInCredentials): AppResult<AuthSessionUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        return when (
            val result = MovitData.account.googleAuth(
                idToken = credentials.idToken,
                googleId = credentials.googleId,
                email = credentials.email,
                name = credentials.name,
                avatarUrl = credentials.avatarUrl,
            )
        ) {
            is AppResult.Success -> AppResult.Success(result.value.toUi())
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }

    override suspend fun forgotPassword(email: String): AppResult<Unit> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }
        return when (val result = MovitData.account.forgotPassword(email)) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }
}

private const val DATA_LAYER_NOT_INSTALLED = "App data layer is not installed."

private fun AuthenticatedSessionResult.toUi(): AuthSessionUi = AuthSessionUi(
    userId = session.userId,
    name = session.name,
    email = session.email,
    guestOutboxCount = guestOutboxPrompt?.guestRowCount,
)
