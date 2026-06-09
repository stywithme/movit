package com.movit.debug

import android.content.Context
import com.movit.core.data.MovitData
import com.movit.core.data.platform.MovitPlatformBindings
import com.trainingvalidator.poc.network.ApiConfig
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.ProgramRepository

object MovitDataInstall {

    fun install(context: Context) {
        val appContext = context.applicationContext
        MovitData.install(
            object : MovitPlatformBindings {
                override fun apiBaseUrl(): String = ApiConfig.getEffectiveBaseUrl()

                override fun authHeader(): String? = AuthManager.getAuthHeader(appContext)

                override fun preferredLanguage(): String =
                    appContext.resources.configuration.locales[0]?.language ?: "en"

                override fun userDisplayName(fallback: String): String =
                    AuthManager.getUserName(appContext, fallback)

                override fun readCache(store: String, key: String): String? =
                    appContext.getSharedPreferences(store, Context.MODE_PRIVATE).getString(key, null)

                override fun writeCache(store: String, key: String, value: String) {
                    appContext.getSharedPreferences(store, Context.MODE_PRIVATE)
                        .edit()
                        .putString(key, value)
                        .apply()
                }

                override fun removeCache(store: String, key: String) {
                    appContext.getSharedPreferences(store, Context.MODE_PRIVATE)
                        .edit()
                        .remove(key)
                        .apply()
                }

                override fun isProUser(): Boolean = AuthManager.isProUser(appContext)

                override fun activeUserProgramId(): String? =
                    ProgramRepository.getInstance(appContext)
                        .getActiveUserProgramExport()
                        ?.takeIf { it.isActive }
                        ?.id
            },
        )
    }
}
