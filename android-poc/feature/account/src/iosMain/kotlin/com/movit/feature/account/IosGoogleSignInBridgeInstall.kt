package com.movit.feature.account

/** Entry point for Swift `iosApp` — exported as `IosGoogleSignInBridgeInstallKt`. */
fun installIosGoogleSignInBridge(bridge: IosGoogleSignInBridge?) {
    IosGoogleSignInBridgeRegistry.install(bridge)
}
