package com.movit.core.data.local

import com.movit.core.data.platform.MovitPlatformBindings

fun interface MovitLocalStoreFactory {
    fun create(platform: MovitPlatformBindings): MovitLocalStore
}

/** Platform-specific SQLDelight driver + migration wrapper. */
internal expect fun createDefaultMovitLocalStore(platform: MovitPlatformBindings): MovitLocalStore

val DefaultMovitLocalStoreFactory = MovitLocalStoreFactory { platform ->
    createDefaultMovitLocalStore(platform)
}
