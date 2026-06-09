package com.movit.feature.reports

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Theme boundary guard — Android JVM only (reads source via java.io.File).
 * [MovitReportsScreen] must not apply [com.movit.designsystem.MovitTheme].
 */
class MovitReportsThemeBoundaryTest {

    @Test
    fun reportsScreen_sourceHasNoMovitThemeWrapper() {
        val source = java.io.File(
            "src/commonMain/kotlin/com/movit/feature/reports/MovitReportsScreen.kt",
        )
        if (!source.exists()) return

        val text = source.readText()
        assertFalse(text.contains("MovitTheme {"), "MovitReportsScreen must not wrap content in MovitTheme")
        assertFalse(text.contains("MovitTheme("), "MovitReportsScreen must not invoke MovitTheme")
    }
}
