package com.movit.feature.account

import com.movit.core.data.MovitData
import com.movit.core.data.platform.AuthSessionSnapshot
import com.movit.shared.AppResult

class SharedAuthRepository(
    private val fallback: AuthRepository = FakeAuthRepository(),
) : AuthRepository {

    override suspend fun login(email: String, password: String): AppResult<AuthSessionUi> {
        if (!MovitData.isInstalled) {
            return fallback.login(email, password)
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
            return fallback.register(name, email, password)
        }
        return when (val result = MovitData.account.register(name, email, password)) {
            is AppResult.Success -> AppResult.Success(result.value.toUi())
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }

    override suspend fun forgotPassword(email: String): AppResult<Unit> {
        if (!MovitData.isInstalled) {
            return fallback.forgotPassword(email)
        }
        return when (val result = MovitData.account.forgotPassword(email)) {
            is AppResult.Success -> AppResult.Success(Unit)
            is AppResult.Failure -> AppResult.Failure(result.message)
        }
    }
}

private fun AuthSessionSnapshot.toUi(): AuthSessionUi = AuthSessionUi(
    userId = userId,
    name = name,
    email = email,
)
