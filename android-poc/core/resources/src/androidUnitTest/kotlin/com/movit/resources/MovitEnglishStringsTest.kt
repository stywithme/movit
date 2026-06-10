package com.movit.resources

import kotlin.test.Test
import kotlin.test.assertTrue

class MovitEnglishStringsTest {

    @Test
    fun movitEnglishStrings_hasNoBackslashEscapes() {
        val offenders = movitEnglishStrings.filter { (_, value) -> value.contains('\\') }
        assertTrue(
            offenders.isEmpty(),
            "movitEnglishStrings must not contain backslash escapes: ${offenders.keys}",
        )
    }
}
