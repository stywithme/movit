package com.movit.feature.reports

import androidx.compose.runtime.Composable

/** Returns an action that shares plain text via the platform sheet, or false when unavailable. */
@Composable
expect fun rememberReportShareAction(): (text: String, chooserTitle: String) -> Boolean
