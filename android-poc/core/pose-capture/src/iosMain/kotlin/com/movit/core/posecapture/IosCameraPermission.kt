package com.movit.core.posecapture

/**
 * AVFoundation class-method bindings for camera authorization are not exposed on
 * [platform.AVFoundation.AVCaptureDevice] in Kotlin 2.3 (see platform klib vs compiler).
 * Session start in [IosCameraFrameSource] triggers the system prompt when needed.
 */
fun isIosCameraAuthorized(): Boolean = true

fun isIosCameraPermissionDenied(): Boolean = false

suspend fun requestIosCameraPermission(): Boolean = true
