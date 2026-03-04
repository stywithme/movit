package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.HomeData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * HomeRepository
 *
 * Offline-first cache for `/api/mobile/home`.
 * - Returns cached content immediately for the dashboard.
 * - Refreshes silently in the background.
 */
class HomeRepository private constructor(private val context: Context) {

    companion object {
        private const val TAG = "HomeRepository"
        private const val PREFS = "home_cache"
        private const val KEY_DATA = "home_data_json"

        @Volatile
        private var instance: HomeRepository? = null

        fun getInstance(context: Context): HomeRepository {
            return instance ?: synchronized(this) {
                instance ?: HomeRepository(context.applicationContext).also { instance = it }
            }
        }

        /** Resets the singleton so a new user gets a fresh instance after logout. */
        fun resetInstance() {
            synchronized(this) { instance = null }
        }
    }

    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val gson = Gson()

    fun getCachedData(): HomeData? {
        val raw = prefs.getString(KEY_DATA, null) ?: return null
        return try {
            gson.fromJson(raw, HomeData::class.java)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "Invalid home cache, clearing", e)
            clear()
            null
        }
    }

    suspend fun syncFromServer(): HomeData? = withContext(Dispatchers.IO) {
        val authHeader = AuthManager.getAuthHeader(context) ?: return@withContext getCachedData()
        
        try {
            val response = ApiClient.mobileSyncApi.getHomeData(authHeader)

            if (!response.isSuccessful || response.body()?.success != true) {
                Log.w(TAG, "Home sync failed: ${response.code()}")
                return@withContext getCachedData()
            }

            val body = response.body() ?: return@withContext getCachedData()
            val incoming = body.data ?: return@withContext getCachedData()

            prefs.edit()
                .putString(KEY_DATA, gson.toJson(incoming))
                .apply()

            incoming
        } catch (e: Exception) {
            Log.w(TAG, "Error syncing home data", e)
            getCachedData()
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
