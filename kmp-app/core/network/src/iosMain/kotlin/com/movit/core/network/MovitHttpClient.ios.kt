package com.movit.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

actual fun createMovitHttpClient(
    enableLogging: Boolean,
    auth: MovitHttpClientConfig?,
): HttpClient = HttpClient(Darwin) {
    expectSuccess = false
    configureMovitHttpClient(enableLogging, auth)
}
