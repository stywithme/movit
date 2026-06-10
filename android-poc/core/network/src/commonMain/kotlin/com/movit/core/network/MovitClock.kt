package com.movit.core.network

object MovitClock {
    var nowEpochMs: () -> Long = { movitPlatformNowEpochMs() }
}

internal fun movitNowEpochMs(): Long = MovitClock.nowEpochMs()

internal expect fun movitPlatformNowEpochMs(): Long
