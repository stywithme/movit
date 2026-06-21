package com.movit.core.network

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
internal actual fun movitPlatformNowEpochMs(): Long = time(null) * 1000L
