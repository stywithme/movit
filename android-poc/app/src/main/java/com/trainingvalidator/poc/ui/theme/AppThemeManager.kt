package com.trainingvalidator.poc.ui.theme

import android.content.Context
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.trainingvalidator.poc.R

object AppThemeManager {
    const val MODE_SYSTEM = "system"
    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    private const val PREFS_NAME = "appearance_preferences"
    private const val KEY_THEME_MODE = "theme_mode"

    val modes: List<String> = listOf(MODE_SYSTEM, MODE_LIGHT, MODE_DARK)

    fun applySavedMode(context: Context) {
        AppCompatDelegate.setDefaultNightMode(toNightMode(getMode(context)))
    }

    fun getMode(context: Context): String {
        val stored = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, MODE_SYSTEM)

        return stored?.takeIf { it in modes } ?: MODE_SYSTEM
    }

    fun setMode(context: Context, mode: String) {
        val normalizedMode = mode.takeIf { it in modes } ?: MODE_SYSTEM
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, normalizedMode)
            .apply()

        AppCompatDelegate.setDefaultNightMode(toNightMode(normalizedMode))
    }

    @StringRes
    fun labelRes(mode: String): Int {
        return when (mode) {
            MODE_LIGHT -> R.string.theme_mode_light
            MODE_DARK -> R.string.theme_mode_dark
            else -> R.string.theme_mode_system
        }
    }

    private fun toNightMode(mode: String): Int {
        return when (mode) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    }
}
