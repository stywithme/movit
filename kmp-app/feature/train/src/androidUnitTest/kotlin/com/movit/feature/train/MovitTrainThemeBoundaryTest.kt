package com.movit.feature.train

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Theme boundary guard — Android JVM only (reads source via java.io.File).
 * [MovitTrainScreen] must not apply [com.movit.designsystem.MovitTheme].
 */
class MovitTrainThemeBoundaryTest {

    @Test
    fun trainScreen_sourceHasNoMovitThemeWrapper() {
        val source = java.io.File(
            "src/commonMain/kotlin/com/movit/feature/train/MovitTrainScreen.kt",
        )
        if (!source.exists()) return

        val text = source.readText()
        assertFalse(text.contains("MovitTheme {"), "MovitTrainScreen must not wrap content in MovitTheme")
        assertFalse(text.contains("MovitTheme("), "MovitTrainScreen must not invoke MovitTheme")
    }
}
