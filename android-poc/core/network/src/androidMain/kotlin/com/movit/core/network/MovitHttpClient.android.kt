package com.movit.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp

actual fun createMovitHttpClient(
    enableLogging: Boolean,
    auth: MovitHttpClientConfig?,
): HttpClient = HttpClient(OkHttp) {
    expectSuccess = false
    configureMovitHttpClient(enableLogging, auth)
}
