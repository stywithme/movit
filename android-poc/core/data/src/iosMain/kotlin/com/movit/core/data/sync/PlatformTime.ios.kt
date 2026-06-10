package com.movit.core.data.sync

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.time

@OptIn(ExperimentalForeignApi::class)
internal actual fun currentTimeMs(): Long = time(null) * 1000L
