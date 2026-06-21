package com.movit.core.posecapture

/** Entry point for Swift `iosApp` — exported as `IosCameraPermissionBridgeInstallKt`. */
fun installIosCameraPermissionBridge(bridge: IosCameraPermissionBridge?) {
    IosCameraPermissionBridgeRegistry.install(bridge)
}
