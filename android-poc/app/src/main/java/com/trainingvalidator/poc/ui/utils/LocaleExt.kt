package com.trainingvalidator.poc.ui.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

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
 * so video overlay text and audio use the same ar/en choice.
 */
fun Context.feedbackLanguageCode(): String {
    val raw = currentLanguage.lowercase()
    return if (raw.startsWith("ar")) "ar" else "en"
}
