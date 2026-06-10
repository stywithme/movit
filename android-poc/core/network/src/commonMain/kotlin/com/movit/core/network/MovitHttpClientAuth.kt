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
import io.ktor.http.contentType
import io.ktor.http.isSuccess

internal const val MOVIT_REFRESH_AHEAD_MS = 60 * 60 * 1000L

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
    refreshAndPersist(refreshHttpClient)
}

internal suspend fun MovitHttpClientConfig.refreshAndPersist(client: HttpClient): BearerTokens? {
    val refreshToken = tokenStore.readRefreshToken()?.takeIf { it.isNotBlank() } ?: return null
    val response = runCatching {
        client.post(refreshUrl()) {
            contentType(ContentType.Application.Json)
            setBody(RefreshTokenRequestDto(refreshToken))
        }
    }.getOrNull() ?: return null

    if (!response.status.isSuccess()) return null
    val body = runCatching { response.body<AuthApiResponse<AuthTokensDto>>() }.getOrNull() ?: return null
    if (!body.success) return null
    val tokens = body.data ?: return null
    val access = tokens.accessToken.takeIf { it.isNotBlank() } ?: return null
    val refresh = tokens.refreshToken.takeIf { it.isNotBlank() } ?: refreshToken
    val expiresAt = if (tokens.expiresIn > 0) {
        movitNowEpochMs() + tokens.expiresIn * 1000L
    } else {
        tokenStore.readExpiresAtEpochMs()
    }
    tokenStore.saveTokens(access, refresh, expiresAt)
    return BearerTokens(access, refresh)
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
                config.refreshAndPersist(config.refreshHttpClient) ?: run {
                    config.handleSessionExpired()
                    null
                }
            }
            sendWithoutRequest { request ->
                !isUnauthenticatedAuthPath(requestPath(request))
            }
        }
    }
}
