package com.movit.resources

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

@OptIn(ExperimentalComposeUiApi::class)
actual object LocalAppLocale {
    private const val LANG_KEY = "AppleLanguages"
    private val default = NSLocale.preferredLanguages.firstOrNull() as? String ?: "en"
    private val LocalAppLocaleState = staticCompositionLocalOf { default }

    actual val current: String
        @Composable get() = LocalAppLocaleState.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val new = value ?: default
        if (value == null) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(LANG_KEY)
        } else {
            val tag = if (value.lowercase() == "ar") "ar" else "en"
            NSUserDefaults.standardUserDefaults.setObject(listOf(tag), LANG_KEY)
        }
        return LocalAppLocaleState.provides(new)
    }
}
