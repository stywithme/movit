package com.movit.resources

import org.jetbrains.compose.resources.StringResource

fun movitString(name: String): StringResource =
    Res.allStringResources[name]
        ?: error("Missing string resource: $name")

suspend fun localizedString(
    language: String,
    name: String,
    vararg formatArgs: Any,
): String {
    if (formatArgs.isEmpty() && language.lowercase() == "en") {
        movitEnglishStrings[name]?.let { return it }
    }
    if (formatArgs.isEmpty() && language.lowercase() == "ar") {
        movitArabicStrings[name]?.let { return it }
    }
    return try {
        localizedString(language, movitString(name), *formatArgs)
    } catch (_: Throwable) {
        val template = movitEnglishStrings[name]
            ?: throw IllegalStateException("Missing string resource: $name")
        formatMovitTemplate(template, *formatArgs)
    }
}

internal fun formatMovitTemplate(template: String, vararg args: Any): String {
    if (args.isEmpty()) return template
    var result = template
    args.forEachIndexed { index, arg ->
        val position = index + 1
        val stringSlot = "%${position}$" + "s"
        val intSlot = "%${position}$" + "d"
        result = result
            .replace(stringSlot, arg.toString())
            .replace(intSlot, arg.toString())
    }
    return result
}
