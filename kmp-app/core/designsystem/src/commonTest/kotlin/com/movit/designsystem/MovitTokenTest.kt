package com.movit.designsystem

import androidx.compose.ui.unit.isUnspecified
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
class MovitTokenTest {

    @Test
    fun spacingScale_matchesPrototype() {
        assertEquals(4, MovitSpacing.xs.value.toInt())
        assertEquals(8, MovitSpacing.sm.value.toInt())
        assertEquals(12, MovitSpacing.md.value.toInt())
        assertEquals(16, MovitSpacing.lg.value.toInt())
        assertEquals(24, MovitSpacing.xl.value.toInt())
        assertEquals(32, MovitSpacing.xxl.value.toInt())
    }

    @Test
    fun radiusScale_matchesPrototype() {
        assertEquals(12, MovitRadius.sm.value.toInt())
        assertEquals(16, MovitRadius.md.value.toInt())
        assertEquals(20, MovitRadius.lg.value.toInt())
        assertEquals(26, MovitRadius.xl.value.toInt())
        assertEquals(999, MovitRadius.full.value.toInt())
    }

    @Test
    fun lightColorScheme_usesBrandPrimary() {
        assertEquals(MovitPalette.Aqua, MovitLightColorScheme.primary)
    }

    @Test
    fun darkColorScheme_usesBrandPrimary() {
        assertEquals(MovitPalette.Aqua, MovitDarkColorScheme.primary)
    }

    @Test
    fun lightColorScheme_errorDistinctFromTertiary() {
        assertTrue(MovitLightColorScheme.error != MovitLightColorScheme.tertiary)
        assertTrue(MovitLightColorScheme.errorContainer != MovitLightColorScheme.tertiaryContainer)
    }

    @Test
    fun darkColorScheme_errorDistinctFromTertiary() {
        assertTrue(MovitDarkColorScheme.error != MovitDarkColorScheme.tertiary)
        assertTrue(MovitDarkColorScheme.errorContainer != MovitDarkColorScheme.tertiaryContainer)
    }

    @Test
    fun displayTypography_hasArabicFriendlyLineHeight() {
        val ratio = MovitTypography.displayLarge.lineHeight.value / MovitTypography.displayLarge.fontSize.value
        assertTrue(ratio >= 1.3f, "Display line-height should be >= 1.3× font size for Arabic")
    }

    @Test
    fun typography_hasNoNegativeLetterSpacing() {
        val styles = listOf(
            MovitTypography.displayLarge,
            MovitTypography.displayMedium,
            MovitTypography.displaySmall,
            MovitTypography.headlineLarge,
            MovitTypography.headlineMedium,
            MovitTypography.headlineSmall,
            MovitTypography.titleLarge,
            MovitTypography.titleMedium,
            MovitTypography.titleSmall,
            MovitTypography.bodyLarge,
            MovitTypography.bodyMedium,
            MovitTypography.bodySmall,
            MovitTypography.labelLarge,
            MovitTypography.labelMedium,
            MovitTypography.labelSmall,
        )
        styles.forEach { style ->
            if (!style.letterSpacing.isUnspecified) {
                assertTrue(
                    style.letterSpacing.value >= 0f,
                    "Negative letterSpacing (${style.letterSpacing.value}) in style at ${style.fontSize}",
                )
            }
        }
    }
}