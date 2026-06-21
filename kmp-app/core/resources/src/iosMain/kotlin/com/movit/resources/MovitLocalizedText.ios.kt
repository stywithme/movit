package com.movit.resources

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDefaults
import platform.Foundation.preferredLanguages

actual suspend fun localizedString(
    language: String,
    resource: StringResource,
    vararg formatArgs: Any,
): String {
    val langKey = "AppleLanguages"
    val previous = NSLocale.preferredLanguages.firstOrNull() as? String
    val tag = if (language.lowercase() == "ar") "ar" else "en"
    NSUserDefaults.standardUserDefaults.setObject(listOf(tag), langKey)
    return try {
        getString(resource, *formatArgs)
    } finally {
        if (previous != null) {
            NSUserDefaults.standardUserDefaults.setObject(listOf(previous), langKey)
        } else {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(langKey)
        }
    }
}
