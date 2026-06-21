package com.movit.core.network

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json

data class MovitHttpClientConfig(
    val tokenStore: MovitAuthTokenStore,
    val baseUrlProvider: () -> String,
    /** Plain client (no Auth plugin) used for refresh POST to avoid recursion. */
    val refreshHttpClient: HttpClient,
    val onSessionExpired: () -> Unit = {},
)

internal fun HttpClientConfig<*>.configureMovitHttpClient(
    enableLogging: Boolean,
    auth: MovitHttpClientConfig?,
) {
    install(ContentNegotiation) {
        json(MovitJson)
    }
    auth?.let { installMovitAuth(it) }
    if (enableLogging) {
        install(Logging) {
            level = LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    println("MovitHttp: $message")
                }
            }
        }
    }
}

fun createMovitHttpClientWithEngine(
    engine: HttpClientEngine,
    enableLogging: Boolean = false,
    auth: MovitHttpClientConfig? = null,
): HttpClient = HttpClient(engine) {
    expectSuccess = false
    configureMovitHttpClient(enableLogging, auth)
}
