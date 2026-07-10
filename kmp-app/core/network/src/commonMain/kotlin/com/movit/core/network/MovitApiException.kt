package com.movit.core.network

/**
 * Typed HTTP failure from [MovitMobileApi] (P3.1).
 * Prefer reading [status] over parsing status codes out of [message].
 */
class MovitApiException(
    val status: Int,
    val body: String? = null,
    message: String = "Request failed ($status)",
    cause: Throwable? = null,
) : Exception(message, cause)
