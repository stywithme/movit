package com.movit.feature.shell

import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.movit.core.data.MovitData
import com.movit.core.data.platform.IosMovitPlatform
import com.movit.designsystem.MovitTheme
import platform.UIKit.UIViewController

/**
 * iOS entry point, consumed by iosApp/ (Swift) through the MovitApp framework.
 *
 * ComposeUIViewController provides [androidx.lifecycle.LifecycleOwner] and
 * [androidx.lifecycle.ViewModelStoreOwner] — ViewModels use [androidx.lifecycle.viewmodel.compose.viewModel].
 */
fun MainViewController(): UIViewController = ComposeUIViewController(
    configure = { enforceStrictPlistSanityCheck = false },
) {
    val platform = remember { IosMovitPlatform() }
    remember { MovitData.install(platform) }
    MovitTheme {
        MovitAppShellRoute()
    }
}
