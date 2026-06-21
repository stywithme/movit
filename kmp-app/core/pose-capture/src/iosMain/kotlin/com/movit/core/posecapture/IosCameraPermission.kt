package com.movit.core.posecapture

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

fun isIosCameraAuthorized(): Boolean =
    cameraAuthorizationStatus() == IOS_CAMERA_AUTH_AUTHORIZED

fun isIosCameraPermissionDenied(): Boolean {
    val status = cameraAuthorizationStatus()
    return status == IOS_CAMERA_AUTH_DENIED || status == IOS_CAMERA_AUTH_RESTRICTED
}

suspend fun requestIosCameraPermission(): Boolean {
    if (isIosCameraAuthorized()) return true
    if (isIosCameraPermissionDenied()) return false
    val bridge = IosCameraPermissionBridgeRegistry.current() ?: return false
    if (cameraAuthorizationStatus() != IOS_CAMERA_AUTH_NOT_DETERMINED) {
        return isIosCameraAuthorized()
    }
    return suspendCancellableCoroutine { cont ->
        bridge.requestAccess(
            object : IosCameraPermissionResultHandler {
                override fun onCompleted(granted: Boolean) {
                    if (cont.isActive) cont.resume(granted)
                }
            },
        )
    }
}

private fun cameraAuthorizationStatus(): Int =
    IosCameraPermissionBridgeRegistry.current()?.authorizationStatus()
        ?: IOS_CAMERA_AUTH_NOT_DETERMINED
