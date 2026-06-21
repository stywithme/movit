package com.movit.core.network

import kotlinx.serialization.json.Json

val MovitJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
}
