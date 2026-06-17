package com.movit.feature.shell

import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene

/**
 * Scene-based top view controller lookup (iOS 15+) — replaces deprecated `keyWindow`.
 */
internal fun iosTopViewController(): UIViewController? {
    val window = iosKeyWindow() ?: return null
    var top = window.rootViewController ?: return null
    while (top.presentedViewController != null) {
        top = top.presentedViewController!!
    }
    return top
}

private fun iosKeyWindow(): UIWindow? {
    val scenes = UIApplication.sharedApplication.connectedScenes
    val enumerator = scenes.objectEnumerator()
    while (true) {
        val scene = enumerator.nextObject() as? UIWindowScene ?: break
        val windows = scene.windows
        val windowEnumerator = windows.objectEnumerator()
        var fallback: UIWindow? = null
        while (true) {
            val window = windowEnumerator.nextObject() as? UIWindow ?: break
            if (fallback == null) fallback = window
            if (window.isKeyWindow) return window
        }
        if (fallback != null) return fallback
    }
    return null
}
