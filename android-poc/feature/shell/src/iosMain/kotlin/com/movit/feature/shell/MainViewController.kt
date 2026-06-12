package com.movit.feature.shell



import androidx.compose.runtime.remember

import androidx.compose.ui.window.ComposeUIViewController

import com.movit.core.data.MovitData
import com.movit.core.data.outbox.registerOutboxConnectivityReplay
import com.movit.core.data.platform.IosMovitPlatform

import platform.UIKit.UIViewController



/**

 * iOS entry point, consumed by iosApp/ (Swift) through the MovitApp framework.

 */

fun MainViewController(): UIViewController = ComposeUIViewController(

    configure = { enforceStrictPlistSanityCheck = false },

) {

    val platform = remember { IosMovitPlatform() }

    remember {
        MovitData.install(platform)
        registerOutboxConnectivityReplay()
    }

    MovitAppShellRoute(
        onShareText = { _, text -> shareTextOnIos(subject = "", text = text) },
    )

}

