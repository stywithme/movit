package com.movit.feature.home

/**
 * Maps local hour to the localized greeting string key used in the app header.
 */
internal object HomeTimeGreeting {
    fun stringKeyForHour(hour: Int): String = when (hour) {
        in 0..11 -> "home_greeting_morning"
        in 12..16 -> "home_greeting_afternoon"
        else -> "home_greeting_evening"
    }

    fun stringKeyNow(): String = stringKeyForHour(currentLocalHour())
}
