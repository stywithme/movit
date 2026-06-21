package com.movit.feature.account

import java.util.Calendar

internal actual fun currentCalendarYear(): Int = Calendar.getInstance().get(Calendar.YEAR)
