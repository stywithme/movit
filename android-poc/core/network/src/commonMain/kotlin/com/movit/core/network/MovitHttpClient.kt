package com.movit.core.network

import io.ktor.client.HttpClient

expect fun createMovitHttpClient(enableLogging: Boolean = false): HttpClient
