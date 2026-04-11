package com.trainingvalidator.poc.storage

import android.util.Log
import java.io.File

/**
 * Shared helpers for JSON-backed entity caches (exercise / workout / program).
 * Reduces duplication across [ExerciseCacheManager], [WorkoutCacheManager], [ProgramCacheManager].
 */
internal object JsonEntityCacheSupport {
    fun ensureDirs(cacheDir: File, itemsDir: File, tag: String) {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
            Log.d(tag, "Created cache directory: ${cacheDir.absolutePath}")
        }
        if (!itemsDir.exists()) {
            itemsDir.mkdirs()
            Log.d(tag, "Created items directory: ${itemsDir.absolutePath}")
        }
    }
}
