package com.movit.core.network

/**
 * Resolves the Movit API base URL for KMP HTTP clients.
 * Android reads [api.properties]/[local.properties] via generated build config; iOS uses the same
 * generated defaults with optional [NSUserDefaults] overrides (`movit.api.*` keys).
 */
object MovitApiConfig {
  const val CONNECT_TIMEOUT_SECONDS = 30L
  const val READ_TIMEOUT_SECONDS = 60L
  const val WRITE_TIMEOUT_SECONDS = 60L

  /** Optional runtime override. Set to null to use mode-based detection. */
  var overrideBaseUrl: String? = null

  fun getEffectiveBaseUrl(): String = overrideBaseUrl ?: resolvePlatformBaseUrl()
}

expect fun resolvePlatformBaseUrl(): String
