package com.movit.feature.training

import com.movit.core.posecapture.isIosCameraAuthorized
import com.movit.core.posecapture.isIosCameraPermissionDenied
import com.movit.core.posecapture.requestIosCameraPermission

internal fun isCameraAuthorized(): Boolean = isIosCameraAuthorized()

internal fun isCameraPermissionDenied(): Boolean = isIosCameraPermissionDenied()

internal suspend fun requestCameraPermission(): Boolean = requestIosCameraPermission()
