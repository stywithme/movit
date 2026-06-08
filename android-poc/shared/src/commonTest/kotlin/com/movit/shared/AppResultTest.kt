package com.movit.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppResultTest {

    @Test
    fun success_getOrNull_returnsValue() {
        val result = AppResult.Success(42)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun failure_getOrNull_returnsNull() {
        val result = AppResult.Failure("error")
        assertNull(result.getOrNull())
    }

    @Test
    fun failure_getOrElse_returnsDefault() {
        val result = AppResult.Failure("error")
        assertEquals("fallback", result.getOrElse { "fallback" })
    }
}
