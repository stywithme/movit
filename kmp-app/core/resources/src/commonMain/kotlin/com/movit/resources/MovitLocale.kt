package com.movit.resources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

val LocalMovitLanguage = compositionLocalOf { "en" }

expect object LocalAppLocale {
    val current: String
        @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

@Composable
fun MovitLocaleProvider(
    languageCode: String,
    content: @Composable () -> Unit,
) {
    val direction = if (languageCode.lowercase() == "ar") {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    CompositionLocalProvider(
        LocalMovitLanguage provides languageCode,
        LocalLayoutDirection provides direction,
        LocalAppLocale provides languageCode,
    ) {
        key(languageCode) {
            content()
        }
    }
}
