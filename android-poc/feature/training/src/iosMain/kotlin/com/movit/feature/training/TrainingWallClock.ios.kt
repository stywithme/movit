package com.movit.feature.training

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun trainingWallClockMs(): Long =
    (NSDate().timeIntervalSince1970 * 1_000.0).toLong()
