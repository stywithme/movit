package com.movit.core.network

import com.movit.core.network.dto.AuthApiResponse
import com.movit.core.network.dto.AuthTokensDto
import com.movit.core.network.dto.RefreshTokenRequestDto
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess

internal const val MOVIT_REFRESH_AHEAD_MS = 60 * 60 * 1000L

/** Outcome of `/mobile/auth/refresh` — only [SessionExpired] clears the local session. */
internal sealed class RefreshOutcome {
    data class Success(val tokens: BearerTokens) : RefreshOutcome()
    /** 401/403 or success=false — refresh token is dead. */
    data object SessionExpired : RefreshOutcome()
    /** 5xx / timeout / IO / parse — keep tokens; caller fails transiently. */
    data object TransientFailure : RefreshOutcome()
}

internal fun shouldRefreshProactively(expiresAtEpochMs: Long, nowEpochMs: Long = movitNowEpochMs()): Boolean =
    expiresAtEpochMs > 0L && nowEpochMs >= expiresAtEpochMs - MOVIT_REFRESH_AHEAD_MS

internal fun formatBearerToken(raw: String): String =
    if (raw.startsWith("Bearer ", ignoreCase = true)) raw else "Bearer $raw"

internal fun requestPath(builder: HttpRequestBuilder): String {
    val segments = builder.url.pathSegments
    return if (segments.isEmpty()) "/" else segments.joinToString(prefix = "/", separator = "/")
}

internal fun isUnauthenticatedAuthPath(encodedPath: String): Boolean {
    val path = encodedPath.lowercase()
    return path.endsWith("/auth/login") ||
        path.endsWith("/auth/register") ||
        path.endsWith("/auth/forgot-password") ||
        path.endsWith("/auth/refresh")
}

internal suspend fun MovitHttpClientConfig.ensureFreshTokens() {
    if (!shouldRefreshProactively(tokenStore.readExpiresAtEpochMs())) return
    when (val outcome = refreshAndPersist(refreshHttpClient)) {
        is RefreshOutcome.SessionExpired -> handleSessionExpired()
        is RefreshOutcome.Success,
        is RefreshOutcome.TransientFailure,
        -> Unit
    }
}

internal suspend fun MovitHttpClientConfig.refreshAndPersist(client: HttpClient): RefreshOutcome {
    val refreshToken = tokenStore.readRefreshToken()?.takeIf { it.isNotBlank() }
        ?: return RefreshOutcome.SessionExpired
    val response = runCatching {
        client.post(refreshUrl()) {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequestDto(refreshToken))
        }
    }.getOrElse { return RefreshOutcome.TransientFailure }

    val status = response.status
    if (status == HttpStatusCode.Unauthorized || status == HttpStatusCode.Forbidden) {
        return RefreshOutcome.SessionExpired
    }
    if (!status.isSuccess()) {
        // 5xx and other non-auth failures are retryable — do not expire the session.
        return RefreshOutcome.TransientFailure
    }

    val body = runCatching { response.body<AuthApiResponse<AuthTokensDto>>() }
        .getOrElse { return RefreshOutcome.TransientFailure }
    if (!body.success) return RefreshOutcome.SessionExpired
    val tokens = body.data ?: return RefreshOutcome.SessionExpired
    val access = tokens.accessToken.takeIf { it.isNotBlank() } ?: return RefreshOutcome.SessionExpired
    val refresh = tokens.refreshToken.takeIf { it.isNotBlank() } ?: refreshToken
    val expiresAt = if (tokens.expiresIn > 0) {
        movitNowEpochMs() + tokens.expiresIn * 1000L
    } else {
        tokenStore.readExpiresAtEpochMs()
    }
    tokenStore.saveTokens(access, refresh, expiresAt)
    return RefreshOutcome.Success(BearerTokens(access, refresh))
}

internal fun MovitHttpClientConfig.handleSessionExpired() {
    tokenStore.clearTokens()
    onSessionExpired()
}

private fun MovitHttpClientConfig.refreshUrl(): String {
    val root = baseUrlProvider().trimEnd('/')
    return "$root/api/mobile/auth/refresh"
}

internal fun io.ktor.client.HttpClientConfig<*>.installMovitAuth(config: MovitHttpClientConfig) {
    install(Auth) {
        bearer {
            loadTokens {
                config.ensureFreshTokens()
                val access = config.tokenStore.readAccessToken()
                val refresh = config.tokenStore.readRefreshToken()
                when {
                    access.isNullOrBlank() || refresh.isNullOrBlank() -> null
                    else -> BearerTokens(access, refresh)
                }
            }
            refreshTokens {
                when (val outcome = config.refreshAndPersist(config.refreshHttpClient)) {
                    is RefreshOutcome.Success -> outcome.tokens
                    is RefreshOutcome.SessionExpired -> {
                        config.handleSessionExpired()
                        null
                    }
                    is RefreshOutcome.TransientFailure -> null
                }
            }
            sendWithoutRequest { request ->
                !isUnauthenticatedAuthPath(requestPath(request))
            }
        }
    }
}
