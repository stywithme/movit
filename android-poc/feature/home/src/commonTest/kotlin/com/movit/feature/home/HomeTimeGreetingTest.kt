package com.movit.feature.home

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeTimeGreetingTest {

    @Test
    fun stringKeyForHour_morning() {
        assertEquals("home_greeting_morning", HomeTimeGreeting.stringKeyForHour(0))
        assertEquals("home_greeting_morning", HomeTimeGreeting.stringKeyForHour(11))
    }

    @Test
    fun stringKeyForHour_afternoon() {
        assertEquals("home_greeting_afternoon", HomeTimeGreeting.stringKeyForHour(12))
        assertEquals("home_greeting_afternoon", HomeTimeGreeting.stringKeyForHour(16))
    }

    @Test
    fun stringKeyForHour_evening() {
        assertEquals("home_greeting_evening", HomeTimeGreeting.stringKeyForHour(17))
        assertEquals("home_greeting_evening", HomeTimeGreeting.stringKeyForHour(23))
    }
}
