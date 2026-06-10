package com.movit.core.network

object MovitClock {
    var nowEpochMs: () -> Long = { movitPlatformNowEpochMs() }

    fun resetToPlatformClock() {
        nowEpochMs = { movitPlatformNowEpochMs() }
    }
}

internal fun movitNowEpochMs(): Long = MovitClock.nowEpochMs()

internal expect fun movitPlatformNowEpochMs(): Long
