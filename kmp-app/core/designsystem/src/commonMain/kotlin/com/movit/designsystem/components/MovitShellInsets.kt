package com.movit.designsystem.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing

/** Bottom scroll inset so tab content clears the shell floating nav pill overlay. */
val MovitFloatingNavContentInset: Dp = 64.dp + MovitSpacing.md + MovitSpacing.lg

/** Provided by [com.movit.feature.shell.MovitAppShell] when the floating nav is visible. */
val LocalMovitFloatingNavContentInset = compositionLocalOf { 0.dp }

/**
 * Extra bottom padding for scrollable content only — keeps the body full-bleed behind the
 * floating nav while letting the last items scroll clear of the pill.
 */
@Composable
fun Modifier.movitFloatingNavScrollPadding(): Modifier {
    val inset = LocalMovitFloatingNavContentInset.current
    return if (inset > 0.dp) padding(bottom = inset) else this
}
