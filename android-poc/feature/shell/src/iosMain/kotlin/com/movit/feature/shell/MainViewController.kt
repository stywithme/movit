package com.movit.feature.shell

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.movit.designsystem.MovitTheme
import com.movit.feature.explore.MovitExploreViewModel
import com.movit.feature.home.MovitHomeViewModel
import platform.UIKit.UIViewController

/**
 * iOS entry point, consumed by iosApp/ (Swift) through the MovitApp framework.
 *
 * Renders the full Movit app shell with Compose Multiplatform. On iOS the feature
 * ViewModels fall back to fake data (the Android debug bridge is not wired here),
 * which is exactly what this render proof needs — verify Compose paints on iOS
 * before any networking exists on the platform.
 *
 * Exposed to Swift as `MainViewControllerKt.MainViewController()`.
 */
fun MainViewController(): UIViewController = ComposeUIViewController(
    configure = { enforceStrictPlistSanityCheck = false },
) {
    MovitTheme {
        MovitAppShellRoute(
            shellViewModel = remember { MovitAppShellViewModel() },
            homeViewModel = remember { MovitHomeViewModel() },
            exploreViewModel = remember { MovitExploreViewModel() },
        )
    }
}
