package com.trainingvalidator.poc.storage

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.trainingvalidator.poc.network.UserProgramExport

/**
 * UserProgramStore
 *
 * Stores user program enrollments from sync.
 */
class UserProgramStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .create()

    fun saveUserPrograms(programs: List<UserProgramExport>) {
        val json = gson.toJson(programs)
        prefs.edit().putString(KEY_USER_PROGRAMS, json).apply()
    }

    fun getUserPrograms(): List<UserProgramExport> {
        val json = prefs.getString(KEY_USER_PROGRAMS, null) ?: return emptyList()
        return runCatching {
            gson.fromJson(json, Array<UserProgramExport>::class.java).toList()
        }.getOrDefault(emptyList())
    }

    fun getActiveUserProgram(): UserProgramExport? {
        return getUserPrograms().firstOrNull { it.isActive }
    }

    companion object {
        private const val PREFS_NAME = "user_program_store"
        private const val KEY_USER_PROGRAMS = "user_programs"
    }
}
