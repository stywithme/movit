package com.movit.feature.training

/**
 * Kotlin 2.3 AVFoundation bindings omit `authorizationStatusForMediaType` / `requestAccessForMediaType`.
 * Defer to [com.movit.core.posecapture.IosCameraFrameSource] session start for the system prompt.
 */
internal fun isCameraAuthorized(): Boolean = true

internal fun isCameraPermissionDenied(): Boolean = false

internal suspend fun requestCameraPermission(): Boolean = true
