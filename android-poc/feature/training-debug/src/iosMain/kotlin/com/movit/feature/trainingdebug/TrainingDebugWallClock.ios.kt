package com.movit.feature.trainingdebug

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

@OptIn(ExperimentalForeignApi::class)
internal actual fun trainingDebugWallClockMs(): Long =
    (NSDate().timeIntervalSince1970 * 1_000.0).toLong()
