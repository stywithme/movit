package com.movit.feature.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberReportShareAction(): (text: String, chooserTitle: String) -> Boolean =
    remember {
        { text, _ ->
            val controller = UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null,
            )
            val root = UIApplication.sharedApplication.keyWindow?.rootViewController
            root?.presentViewController(controller, animated = true, completion = null)
            root != null
        }
    }
