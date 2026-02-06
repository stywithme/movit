package com.trainingvalidator.poc.training.loader

import android.content.res.AssetManager
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.StateMessageValue
import com.trainingvalidator.poc.training.models.StateMessageValueTypeAdapter
import java.io.IOException
import java.io.InputStreamReader

/**
 * ExerciseLoader - Loads exercise configurations from assets or cache
 * 
 * Reads JSON files from assets/exercises/ folder and parses them
 * into ExerciseConfig objects.
 * 
 * For new code, prefer using ExerciseRepository which:
 * - Supports server sync
 * - Provides cached data
 * - Handles offline mode
 * 
 * This loader is kept for:
 * - Loading bundled assets as fallback
 * - Backwards compatibility
 * - Testing
 */
object ExerciseLoader {
    
    private const val TAG = "ExerciseLoader"
    private const val EXERCISES_FOLDER = "exercises"
    
    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .registerTypeAdapter(StateMessageValue::class.java, StateMessageValueTypeAdapter())
        .create()
    
    /**
     * Get configured Gson instance (shared with other components)
     */
    fun getGson(): Gson = gson
    
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
                    val rawConfig = gson.fromJson(reader, ExerciseConfig::class.java)
                    // Sanitize Gson-null fields (Gson ignores Kotlin default values)
                    val config = rawConfig.sanitizeGsonDefaults()
                    // Store the file name for later use
                    config.fileName = exerciseName
                    Log.d(TAG, "Loaded exercise: ${config.name.en} (file: $exerciseName)")
                    
                    // Validate StateRanges configuration
                    validateStateRanges(config, exerciseName)
                    
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
     * Validate StateRanges configuration and log warnings
     */
    private fun validateStateRanges(config: ExerciseConfig, exerciseName: String) {
        for (variant in config.poseVariants) {
            for (joint in variant.trackedJoints) {
                // Validate upRange
                joint.upRange?.let { upRange ->
                    val result = upRange.validate(joint.joint, "upRange")
                    result.warnings.forEach { warning ->
                        Log.w(TAG, "[$exerciseName] $warning")
                    }
                    result.errors.forEach { error ->
                        Log.e(TAG, "[$exerciseName] $error")
                    }
                }
                
                // Validate downRange
                joint.downRange?.let { downRange ->
                    val result = downRange.validate(joint.joint, "downRange")
                    result.warnings.forEach { warning ->
                        Log.w(TAG, "[$exerciseName] $warning")
                    }
                    result.errors.forEach { error ->
                        Log.e(TAG, "[$exerciseName] $error")
                    }
                }
                
                // Validate hold range (for SECONDARY joints)
                joint.range?.let { holdRange ->
                    val result = holdRange.validate(joint.joint, "range")
                    result.warnings.forEach { warning ->
                        Log.w(TAG, "[$exerciseName] $warning")
                    }
                    result.errors.forEach { error ->
                        Log.e(TAG, "[$exerciseName] $error")
                    }
                }
                
                // Validate TRANSITION zone (upRange.min > downRange.max)
                if (joint.upRange != null && joint.downRange != null) {
                    val transitionZone = joint.getTransitionZone()
                    if (transitionZone == null) {
                        Log.w(TAG, "[$exerciseName][${joint.joint}] No valid TRANSITION zone: upRange.min (${joint.upRange.effectiveMin}) should be > downRange.max (${joint.downRange.effectiveMax})")
                    } else {
                        val (transMin, transMax) = transitionZone
                        if (transMax - transMin < 5.0) {
                            Log.w(TAG, "[$exerciseName][${joint.joint}] TRANSITION zone is very small (${transMin}-${transMax}). Consider increasing the gap.")
                        }
                    }
                }
            }
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
     * Load exercise from JSON string (for testing or cache parsing)
     */
    fun loadFromJson(json: String): ExerciseConfig? {
        return try {
            val config = gson.fromJson(json, ExerciseConfig::class.java)
            // Sanitize Gson-null fields (Gson ignores Kotlin default values)
            config?.sanitizeGsonDefaults()
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JSON", e)
            null
        }
    }
    
    /**
     * Validate an ExerciseConfig and return validation results
     * Useful when loading from cache or network
     */
    fun validateExercise(config: ExerciseConfig): ValidationResult {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()
        
        for (variant in config.poseVariants) {
            for (joint in variant.trackedJoints) {
                joint.upRange?.let { upRange ->
                    val result = upRange.validate(joint.joint, "upRange")
                    warnings.addAll(result.warnings)
                    errors.addAll(result.errors)
                }
                
                joint.downRange?.let { downRange ->
                    val result = downRange.validate(joint.joint, "downRange")
                    warnings.addAll(result.warnings)
                    errors.addAll(result.errors)
                }
                
                joint.range?.let { holdRange ->
                    val result = holdRange.validate(joint.joint, "range")
                    warnings.addAll(result.warnings)
                    errors.addAll(result.errors)
                }
                
                if (joint.upRange != null && joint.downRange != null) {
                    val transitionZone = joint.getTransitionZone()
                    if (transitionZone == null) {
                        warnings.add("[${joint.joint}] No valid TRANSITION zone")
                    }
                }
            }
        }
        
        return ValidationResult(
            isValid = errors.isEmpty(),
            warnings = warnings,
            errors = errors
        )
    }
    
    data class ValidationResult(
        val isValid: Boolean,
        val warnings: List<String>,
        val errors: List<String>
    )
}
