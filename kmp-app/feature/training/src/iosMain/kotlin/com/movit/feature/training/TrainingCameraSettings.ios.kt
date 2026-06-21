package com.movit.feature.training

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

@Composable
actual fun rememberOpenAppSettingsAction(): () -> Unit =
    remember {
        {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
                UIApplication.sharedApplication.openURL(url)
            }
        }
    }
