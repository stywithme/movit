package com.trainingvalidator.poc.training.loader

import android.content.res.AssetManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.trainingvalidator.poc.training.models.ExerciseConfig
import java.io.IOException
import java.io.InputStreamReader

/**
 * ExerciseLoader - Loads exercise configurations from assets
 * 
 * Reads JSON files from assets/exercises/ folder and parses them
 * into ExerciseConfig objects.
 */
object ExerciseLoader {
    
    private const val TAG = "ExerciseLoader"
    private const val EXERCISES_FOLDER = "exercises"
    
    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .create()
    
    /**
     * Load a single exercise by name
     * @param assets AssetManager from context
     * @param exerciseName Name of the exercise file (without .json extension)
     * @return ExerciseConfig or null if failed
     */
    fun load(assets: AssetManager, exerciseName: String): ExerciseConfig? {
        val fileName = "$EXERCISES_FOLDER/$exerciseName.json"
        
        return try {
            assets.open(fileName).use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val config = gson.fromJson(reader, ExerciseConfig::class.java)
                    // Store the file name for later use
                    config.fileName = exerciseName
                    Log.d(TAG, "Loaded exercise: ${config.name.en} (file: $exerciseName)")
                    config
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load exercise: $fileName", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing exercise: $fileName", e)
            null
        }
    }
    
    /**
     * Load all available exercises
     * @param assets AssetManager from context
     * @return List of ExerciseConfig
     */
    fun loadAll(assets: AssetManager): List<ExerciseConfig> {
        val exercises = mutableListOf<ExerciseConfig>()
        
        try {
            val files = assets.list(EXERCISES_FOLDER) ?: return emptyList()
            
            for (file in files) {
                if (file.endsWith(".json")) {
                    val exerciseName = file.removeSuffix(".json")
                    load(assets, exerciseName)?.let { config ->
                        exercises.add(config)
                    }
                }
            }
            
            Log.d(TAG, "Loaded ${exercises.size} exercises")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to list exercises folder", e)
        }
        
        return exercises
    }
    
    /**
     * Get list of available exercise names
     * @param assets AssetManager from context
     * @return List of exercise names (without .json extension)
     */
    fun getAvailableExercises(assets: AssetManager): List<String> {
        return try {
            assets.list(EXERCISES_FOLDER)
                ?.filter { it.endsWith(".json") }
                ?.map { it.removeSuffix(".json") }
                ?: emptyList()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to list exercises", e)
            emptyList()
        }
    }
    
    /**
     * Load exercise from JSON string (for testing)
     */
    fun loadFromJson(json: String): ExerciseConfig? {
        return try {
            gson.fromJson(json, ExerciseConfig::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON", e)
            null
        }
    }
}
