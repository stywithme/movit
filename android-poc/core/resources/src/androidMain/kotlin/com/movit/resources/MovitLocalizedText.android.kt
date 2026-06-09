package com.movit.resources

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.util.Locale

actual suspend fun localizedString(
    language: String,
    resource: StringResource,
    vararg formatArgs: Any,
): String {
    val previous = Locale.getDefault()
    return try {
        val tag = if (language.lowercase() == "ar") "ar" else "en"
        Locale.setDefault(Locale.forLanguageTag(tag))
        getString(resource, *formatArgs)
    } finally {
        Locale.setDefault(previous)
    }
}
