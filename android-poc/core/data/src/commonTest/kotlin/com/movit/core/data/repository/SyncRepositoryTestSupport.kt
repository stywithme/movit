package com.movit.core.data.repository

import com.movit.core.network.MovitJson
import com.movit.core.network.MovitMobileApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

internal fun testMobileApi(engine: MockEngine): MovitMobileApi {
    val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) {
            json(MovitJson)
        }
    }
    return MovitMobileApi(client) { "https://test.movit.local/" }
}

internal fun successJson(body: String): String = body
