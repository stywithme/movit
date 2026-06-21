package com.movit.feature.shell

import platform.UIKit.UIActivityViewController

fun shareTextOnIos(subject: String, text: String): Boolean {
    val root = iosTopViewController() ?: return false
    val activity = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null,
    )
    root.presentViewController(activity, animated = true, completion = null)
    return true
}
