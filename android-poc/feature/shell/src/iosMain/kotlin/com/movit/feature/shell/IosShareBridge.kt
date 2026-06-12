package com.movit.feature.shell

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

fun shareTextOnIos(subject: String, text: String): Boolean {
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController
        ?: return false
    val activity = UIActivityViewController(
        activityItems = listOf(text),
        applicationActivities = null,
    )
    root.presentViewController(activity, animated = true, completion = null)
    return true
}
