package com.movit.core.data.billing

/** Entry point for Swift `iosApp` — exported as `IosStoreKitBridgeInstallKt`. */
fun installIosStoreKitBridge(bridge: IosStoreKitBridge?) {
    IosStoreKitBridgeRegistry.install(bridge)
}
