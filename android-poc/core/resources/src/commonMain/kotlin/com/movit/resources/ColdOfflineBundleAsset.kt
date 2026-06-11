package com.movit.resources

import org.jetbrains.compose.resources.ExperimentalResourceApi

private const val BUNDLED_COLD_OFFLINE_PATH = "files/cold_offline_bundle.json"

@OptIn(ExperimentalResourceApi::class)
suspend fun readBundledColdOfflineJson(): String =
    Res.readBytes(BUNDLED_COLD_OFFLINE_PATH).decodeToString()
