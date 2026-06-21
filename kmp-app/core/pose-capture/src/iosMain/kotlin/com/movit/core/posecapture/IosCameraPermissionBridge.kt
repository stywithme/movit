package com.movit.core.posecapture

/**
 * Swift `MovitCameraPermissionBridge` implements this protocol (AVFoundation).
 * Registered via [installIosCameraPermissionBridge] from `iosApp` init.
 */
interface IosCameraPermissionBridge {
    fun authorizationStatus(): Int

    fun requestAccess(handler: IosCameraPermissionResultHandler)
}

interface IosCameraPermissionResultHandler {
    fun onCompleted(granted: Boolean)
}

object IosCameraPermissionBridgeRegistry {
    private var bridge: IosCameraPermissionBridge? = null

    fun current(): IosCameraPermissionBridge? = bridge

    internal fun install(bridge: IosCameraPermissionBridge?) {
        this.bridge = bridge
    }
}

/** AVFoundation authorization status values (match `AVAuthorizationStatus`). */
const val IOS_CAMERA_AUTH_NOT_DETERMINED = 0
const val IOS_CAMERA_AUTH_RESTRICTED = 1
const val IOS_CAMERA_AUTH_DENIED = 2
const val IOS_CAMERA_AUTH_AUTHORIZED = 3
