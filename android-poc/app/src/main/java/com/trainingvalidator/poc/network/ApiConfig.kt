package com.trainingvalidator.poc.network

import android.os.Build
import com.trainingvalidator.poc.BuildConfig

/**
 * API Configuration
 *
 * Switch mode in local.properties:
 *   api.mode=local   → uses emulator (10.0.2.2) or LAN IP
 *   api.mode=server  → uses https://back.mongz.online/
 *
 * Local mode settings:
 *   api.port=4000
 *   api.physical_device_ip=192.168.68.184
 *
 * Server mode settings:
 *   api.server_url=https://back.mongz.online/
 */
object ApiConfig {

    private val MODE = BuildConfig.API_MODE         // "local" | "server"
    private val PORT = BuildConfig.API_PORT
    private val PHYSICAL_DEVICE_IP = BuildConfig.API_PHYSICAL_IP
    private val SERVER_URL = BuildConfig.API_SERVER_URL

    private const val EMULATOR_HOST = "10.0.2.2"

    private val BASE_URL_EMULATOR = "http://$EMULATOR_HOST:$PORT/"
    private val BASE_URL_PHYSICAL = "http://$PHYSICAL_DEVICE_IP:$PORT/"

    const val CONNECT_TIMEOUT = 30L
    const val READ_TIMEOUT    = 60L
    const val WRITE_TIMEOUT   = 60L

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("google/sdk_gphone")
                || Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.contains("emulator")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")
                || Build.PRODUCT == "google_sdk"
                || Build.PRODUCT == "sdk_gphone64_arm64"
                || Build.PRODUCT.startsWith("sdk_gphone"))
    }

    /**
     * Returns base URL based on current mode:
     * - "server" → production server (HTTPS)
     * - "local"  → emulator or LAN IP (auto-detected)
     */
    fun getBaseUrl(): String {
        return when (MODE) {
            "server" -> SERVER_URL
            else     -> if (isEmulator()) BASE_URL_EMULATOR else BASE_URL_PHYSICAL
        }
    }

    /** Optional runtime override. Set to null to use mode-based detection. */
    var overrideBaseUrl: String? = null

    fun getEffectiveBaseUrl(): String = overrideBaseUrl ?: getBaseUrl()
}
