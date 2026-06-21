package com.movit.feature.home

import java.util.Calendar

internal actual fun currentLocalHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
