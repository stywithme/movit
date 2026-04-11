package com.trainingvalidator.poc.storage

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.trainingvalidator.poc.network.ExerciseConfigWithMeta
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.StateMessageValue
import com.trainingvalidator.poc.training.models.StateMessageValueTypeAdapter

/**
 * Loads a minimal bundled exercise when the cache is empty and the device is offline
 * (first install with no network). Asset: [ASSET_PATH].
 */
object OfflineFallbackLoader {

    private const val TAG = "OfflineFallback"
    private const val ASSET_PATH = "offline_fallback/desk_test.json"

    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .registerTypeAdapter(StateMessageValue::class.java, StateMessageValueTypeAdapter())
        .create()

    /**
     * @return One exercise with synthetic ids, or empty list if asset missing / parse error.
     */
    fun loadBundledExerciseMeta(context: Context): List<ExerciseConfigWithMeta> {
        return try {
            context.assets.open(ASSET_PATH).use { input ->
                val config = gson.fromJson(
                    input.bufferedReader(),
                    ExerciseConfig::class.java
                )
                listOf(
                    ExerciseConfigWithMeta.fromExerciseConfig(
                        id = "offline-bundled-desk-test",
                        slug = "desk_test",
                        updatedAt = "1970-01-01T00:00:00.000Z",
                        config = config
                    )
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "No offline fallback exercise loaded: ${e.message}")
            emptyList()
        }
    }
}
