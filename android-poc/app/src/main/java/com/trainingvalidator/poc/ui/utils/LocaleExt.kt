package com.trainingvalidator.poc.ui.utils

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.network.PlanLevelData
import com.trainingvalidator.poc.training.models.ProgramLevelConfig

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

fun Context.formatProgramLevel(levelMin: ProgramLevelConfig?, levelMax: ProgramLevelConfig?): String {
    return formatProgramLevelNumbers(levelMin?.number, levelMax?.number)
}

fun Context.formatPlanProgramLevel(levelMin: PlanLevelData?, levelMax: PlanLevelData?): String {
    return formatProgramLevelNumbers(levelMin?.number, levelMax?.number)
}

private fun Context.formatProgramLevelNumbers(min: Int?, max: Int?): String {
    val a = (min ?: 0).coerceAtLeast(0)
    val b = (max ?: 0).coerceAtLeast(0)
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
