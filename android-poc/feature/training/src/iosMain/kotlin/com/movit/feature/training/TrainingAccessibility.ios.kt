package com.movit.feature.training

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

@Composable
actual fun rememberPrefersReducedMotion(): Boolean =
    remember { UIAccessibilityIsReduceMotionEnabled() }