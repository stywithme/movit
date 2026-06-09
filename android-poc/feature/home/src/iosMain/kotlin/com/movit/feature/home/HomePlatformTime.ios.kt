package com.movit.feature.home

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSDate

@OptIn(ExperimentalForeignApi::class)
internal actual fun currentLocalHour(): Int {
    val calendar = NSCalendar.currentCalendar
    return calendar.component(NSCalendarUnitHour, fromDate = NSDate()).toInt()
}
