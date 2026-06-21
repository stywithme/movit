package com.movit.billing

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Active app language for billing UI (mirrors legacy [com.movit.ui.utils.currentLanguage]).
 */
val Context.billingLanguage: String
    get() {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locale = if (appLocales.isEmpty) {
            resources.configuration.locales[0]
        } else {
            appLocales[0]
        }
        return locale?.language ?: "en"
    }
