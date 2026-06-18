package com.movit.feature.shell

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

/**
 * Top view controller for UIKit presentation (share sheets, etc.).
 * Uses UIApplication.keyWindow — same pattern as ReportPlatformShare.ios.kt.
 */
internal fun iosTopViewController(): UIViewController? {
    val window = iosKeyWindow() ?: return null
    var top = window.rootViewController ?: return null
    while (top.presentedViewController != null) {
        top = top.presentedViewController!!
    }
    return top
}

private fun iosKeyWindow(): UIWindow? =
    UIApplication.sharedApplication.keyWindow
