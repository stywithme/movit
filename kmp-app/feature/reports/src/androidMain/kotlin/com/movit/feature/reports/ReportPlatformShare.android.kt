package com.movit.feature.reports

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberReportShareAction(): (text: String, chooserTitle: String) -> Boolean {
    val context = LocalContext.current
    return remember(context) {
        { text, chooserTitle ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(intent, chooserTitle))
            true
        }
    }
}
