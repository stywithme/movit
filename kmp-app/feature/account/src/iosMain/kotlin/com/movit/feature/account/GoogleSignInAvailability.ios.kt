package com.movit.feature.account

actual fun isGoogleSignInBridgeAvailable(): Boolean =
    IosGoogleSignInBridgeRegistry.current()?.isAvailable == true
