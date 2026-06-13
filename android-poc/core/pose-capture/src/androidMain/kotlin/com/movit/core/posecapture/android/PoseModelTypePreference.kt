package com.movit.core.posecapture.android

import android.content.Context

/**
 * Reads the pose model type from the same SharedPreferences store as app [SettingsManager].
 * Keeps core/pose-capture independent of the app module.
 */
internal object PoseModelTypePreference {
    private const val PREFS_NAME = "training_settings"
    private const val KEY_MODEL_TYPE = "model_type"

    fun getModelType(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MODEL_TYPE, "full")
            ?: "full"
}
