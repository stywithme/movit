package com.movit.feature.home

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Theme boundary guard — Android JVM only (reads source via java.io.File).
 * [MovitHomeScreen] must not apply [com.movit.designsystem.MovitTheme].
 */
class MovitHomeThemeBoundaryTest {

    @Test
    fun homeScreen_sourceHasNoMovitThemeWrapper() {
        val source = java.io.File(
            "src/commonMain/kotlin/com/movit/feature/home/MovitHomeScreen.kt",
        )
        if (!source.exists()) return

        val text = source.readText()
        assertFalse(text.contains("MovitTheme {"), "MovitHomeScreen must not wrap content in MovitTheme")
        assertFalse(text.contains("MovitTheme("), "MovitHomeScreen must not invoke MovitTheme")
    }
}
