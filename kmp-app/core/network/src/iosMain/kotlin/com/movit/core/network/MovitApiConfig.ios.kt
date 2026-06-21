package com.movit.core.network

import com.movit.core.network.buildconfig.MovitGeneratedBuildConfig
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSUserDefaults

private const val KEY_BASE_URL_OVERRIDE = "movit.api.base_url_override"
private const val KEY_API_MODE = "movit.api.mode"
private const val KEY_API_SERVER_URL = "movit.api.server_url"
private const val KEY_API_PORT = "movit.api.port"
private const val KEY_API_PHYSICAL_IP = "movit.api.physical_device_ip"

private fun ensureTrailingSlash(url: String): String =
    if (url.endsWith("/")) url else "$url/"

@OptIn(ExperimentalForeignApi::class)
actual fun resolvePlatformBaseUrl(): String {
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.stringForKey(KEY_BASE_URL_OVERRIDE)
        ?.takeIf { it.isNotBlank() }
        ?.let { return ensureTrailingSlash(it) }

    val mode = defaults.stringForKey(KEY_API_MODE) ?: MovitGeneratedBuildConfig.API_MODE
    return when (mode) {
        "server" -> {
            val url = defaults.stringForKey(KEY_API_SERVER_URL)
                ?: MovitGeneratedBuildConfig.API_SERVER_URL
            ensureTrailingSlash(url)
        }
        else -> {
            val port = defaults.stringForKey(KEY_API_PORT)?.toIntOrNull()
                ?: MovitGeneratedBuildConfig.API_PORT
            val host = defaults.stringForKey(KEY_API_PHYSICAL_IP)
                ?: MovitGeneratedBuildConfig.API_PHYSICAL_IP
            "http://$host:$port/"
        }
    }
}
