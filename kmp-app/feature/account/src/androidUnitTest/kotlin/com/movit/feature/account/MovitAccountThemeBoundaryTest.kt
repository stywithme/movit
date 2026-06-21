package com.movit.feature.account

import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Theme boundary guard — Android JVM only (reads source via java.io.File).
 * Account feature screens must not apply [com.movit.designsystem.MovitTheme];
 * the shell host wraps [MovitAppShellRoute] once at the activity root.
 */
class MovitAccountThemeBoundaryTest {

    @Test
    fun authScreen_sourceHasNoMovitThemeWrapper() {
        assertScreenHasNoMovitTheme("MovitAuthScreen.kt")
    }

    @Test
    fun profileScreen_sourceHasNoMovitThemeWrapper() {
        assertScreenHasNoMovitTheme("MovitProfileScreen.kt")
    }

    @Test
    fun assessmentScreen_sourceHasNoMovitThemeWrapper() {
        assertScreenHasNoMovitTheme("MovitAssessmentScreen.kt")
    }

    private fun assertScreenHasNoMovitTheme(fileName: String) {
        val source = java.io.File("src/commonMain/kotlin/com/movit/feature/account/$fileName")
        if (!source.exists()) return

        val text = source.readText()
        assertFalse(text.contains("MovitTheme {"), "$fileName must not wrap content in MovitTheme")
        assertFalse(text.contains("MovitTheme("), "$fileName must not invoke MovitTheme")
    }
}
