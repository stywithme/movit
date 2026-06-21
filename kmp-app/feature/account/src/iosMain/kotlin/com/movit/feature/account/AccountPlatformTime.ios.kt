package com.movit.feature.account

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDate

@OptIn(ExperimentalForeignApi::class)
internal actual fun currentCalendarYear(): Int {
    val calendar = NSCalendar.currentCalendar
    return calendar.component(NSCalendarUnitYear, fromDate = NSDate()).toInt()
}
