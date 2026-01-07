package com.trainingvalidator.poc.training.loader

import android.content.res.AssetManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.trainingvalidator.poc.training.models.WorkoutConfig
import java.io.IOException
import java.io.InputStreamReader

/**
 * WorkoutLoader - Loads workout configurations from assets
 * 
 * Reads JSON files from assets/workouts/ folder and parses them
 * into WorkoutConfig objects.
 * 
 * Similar to ExerciseLoader but for workout/set configurations.
 */
object WorkoutLoader {
    
    private const val TAG = "WorkoutLoader"
    private const val WORKOUTS_FOLDER = "workouts"
    
    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .create()
    
    /**
     * Load a single workout by name
     * @param assets AssetManager from context
     * @param workoutName Name of the workout file (without .json extension)
     * @return WorkoutConfig or null if failed
     */
    fun load(assets: AssetManager, workoutName: String): WorkoutConfig? {
        val fileName = "$WORKOUTS_FOLDER/$workoutName.json"
        
        return try {
            assets.open(fileName).use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val config = gson.fromJson(reader, WorkoutConfig::class.java)
                    // Store the file name for later use
                    config.fileName = workoutName
                    Log.d(TAG, "Loaded workout: ${config.name.en} (file: $workoutName)")
                    config
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load workout: $fileName", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing workout: $fileName", e)
            null
        }
    }
    
    /**
     * Load all available workouts
     * @param assets AssetManager from context
     * @return List of WorkoutConfig
     */
    fun loadAll(assets: AssetManager): List<WorkoutConfig> {
        val workouts = mutableListOf<WorkoutConfig>()
        
        try {
            val files = assets.list(WORKOUTS_FOLDER) ?: return emptyList()
            
            for (file in files) {
                if (file.endsWith(".json")) {
                    val workoutName = file.removeSuffix(".json")
                    load(assets, workoutName)?.let { config ->
                        workouts.add(config)
                    }
                }
            }
            
            Log.d(TAG, "Loaded ${workouts.size} workouts")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to list workouts folder", e)
        }
        
        return workouts
    }
    
    /**
     * Get list of available workout names
     * @param assets AssetManager from context
     * @return List of workout names (without .json extension)
     */
    fun getAvailableWorkouts(assets: AssetManager): List<String> {
        return try {
            assets.list(WORKOUTS_FOLDER)
                ?.filter { it.endsWith(".json") }
                ?.map { it.removeSuffix(".json") }
                ?: emptyList()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to list workouts", e)
            emptyList()
        }
    }
    
    /**
     * Load workout from JSON string (for testing)
     */
    fun loadFromJson(json: String): WorkoutConfig? {
        return try {
            gson.fromJson(json, WorkoutConfig::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON", e)
            null
        }
    }
    
    /**
     * Check if workouts folder exists and has content
     */
    fun hasWorkouts(assets: AssetManager): Boolean {
        return try {
            val files = assets.list(WORKOUTS_FOLDER)
            files != null && files.any { it.endsWith(".json") }
        } catch (e: IOException) {
            false
        }
    }
}
