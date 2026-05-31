package com.trainingvalidator.poc.ui.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.trainingvalidator.poc.R

/**
 * Returns the active app language code (e.g. "en", "ar").
 * Uses the per-app locale (AppCompatDelegate) if set,
 * otherwise falls back to the system/resource locale.
 */
val Context.currentLanguage: String
    get() {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locale = if (appLocales.isEmpty) {
            resources.configuration.locales[0]
        } else {
            appLocales[0]
        }
        return locale?.language ?: "en"
    }

/**
 * Language code for training feedback (text + TTS + cached audio).
 * Must match [currentLanguage] (Profile / app locale), not a static JSON language field,
 * so UI text and audio use the same ar/en choice.
 */
fun Context.feedbackLanguageCode(): String {
    val raw = currentLanguage.lowercase()
    return if (raw.startsWith("ar")) "ar" else "en"
}

/**
 * User-visible label for a program's allowed trainee level span from the API.
 */
fun Context.formatProgramLevelRange(min: Int, max: Int): String {
    val a = min.coerceAtLeast(0)
    val b = max.coerceAtLeast(0)
    if (a <= 0 && b <= 0) {
        return getString(R.string.program_level_unknown)
    }
    if (a > 0 && b > 0) {
        return if (a == b) {
            getString(R.string.program_level_single_format, a)
        } else {
            getString(R.string.program_level_range_format, a, b)
        }
    }
    val single = if (a > 0) a else b
    return getString(R.string.program_level_single_format, single)
}
