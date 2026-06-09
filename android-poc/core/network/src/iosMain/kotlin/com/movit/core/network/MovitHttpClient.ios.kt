package com.movit.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json

actual fun createMovitHttpClient(enableLogging: Boolean): HttpClient = HttpClient(Darwin) {
    install(ContentNegotiation) {
        json(MovitJson)
    }
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
