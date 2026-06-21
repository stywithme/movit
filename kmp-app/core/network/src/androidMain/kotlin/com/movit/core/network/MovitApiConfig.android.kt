package com.movit.core.network

import android.os.Build
import com.movit.core.network.buildconfig.MovitGeneratedBuildConfig

private const val EMULATOR_HOST = "10.0.2.2"

private fun isEmulator(): Boolean {
  return Build.FINGERPRINT.startsWith("google/sdk_gphone") ||
    Build.FINGERPRINT.startsWith("generic") ||
    Build.FINGERPRINT.contains("emulator") ||
    Build.MODEL.contains("Emulator") ||
    Build.MODEL.contains("Android SDK built for x86") ||
    Build.MANUFACTURER.contains("Genymotion") ||
    Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic") ||
    Build.PRODUCT == "google_sdk" ||
    Build.PRODUCT == "sdk_gphone64_arm64" ||
    Build.PRODUCT.startsWith("sdk_gphone")
}

internal fun resolveAndroidApiBaseUrl(): String {
  return when (MovitGeneratedBuildConfig.API_MODE) {
    "server" -> MovitGeneratedBuildConfig.API_SERVER_URL
    else -> {
      val port = MovitGeneratedBuildConfig.API_PORT
      if (isEmulator()) {
        "http://$EMULATOR_HOST:$port/"
      } else {
        "http://${MovitGeneratedBuildConfig.API_PHYSICAL_IP}:$port/"
      }
    }
  }
}

actual fun resolvePlatformBaseUrl(): String = resolveAndroidApiBaseUrl()
