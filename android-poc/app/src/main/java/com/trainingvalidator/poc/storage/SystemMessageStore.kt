package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.trainingvalidator.poc.network.SystemMessageTemplate
import com.trainingvalidator.poc.training.feedback.SystemMessageRegistry

/**
 * Persists [SystemMessageTemplate] list from sync so registry is available before next sync.
 */
class SystemMessageStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .create()

    fun save(templates: List<SystemMessageTemplate>) {
        val json = gson.toJson(templates)
        prefs.edit().putString(KEY_TEMPLATES, json).apply()
        SystemMessageRegistry.replaceAll(templates)
        Log.d(TAG, "Saved ${templates.size} system messages")
    }

    /** Load from disk into [SystemMessageRegistry] (e.g. on app start). */
    fun loadIntoRegistry() {
        val json = prefs.getString(KEY_TEMPLATES, null) ?: return
        val list = runCatching {
            gson.fromJson(json, Array<SystemMessageTemplate>::class.java).toList()
        }.getOrElse { emptyList() }
        if (list.isNotEmpty()) {
            SystemMessageRegistry.replaceAll(list)
            Log.d(TAG, "Loaded ${list.size} system messages from cache")
        }
    }

    companion object {
        private const val TAG = "SystemMessageStore"
        private const val PREFS_NAME = "system_message_store"
        private const val KEY_TEMPLATES = "system_message_templates"
    }
}
