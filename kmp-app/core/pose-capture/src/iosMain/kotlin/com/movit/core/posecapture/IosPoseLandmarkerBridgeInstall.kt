package com.movit.core.posecapture

/** Entry point for Swift `iosApp` — exported as `IosPoseLandmarkerBridgeInstallKt`. */
fun installIosPoseLandmarkerBridge(bridge: IosPoseLandmarkerBridge?) {
    IosPoseLandmarkerBridgeRegistry.install(bridge)
}
