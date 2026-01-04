package com.trainingvalidator.poc.training.config

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.Strictness

/**
 * SettingsManager - Singleton for loading and accessing global app settings
 * 
 * Loads settings from app_settings.json in assets folder.
 * Provides default values if file is missing or parsing fails.
 */
object SettingsManager {
    
    private const val TAG = "SettingsManager"
    private const val SETTINGS_FILE = "app_settings.json"
    
    private var _settings: AppSettings? = null
    
    /**
     * Current app settings (cached)
     */
    val settings: AppSettings
        get() = _settings ?: AppSettings()
    
    /**
     * Check if settings have been loaded
     */
    val isLoaded: Boolean
        get() = _settings != null
    
    private val gson: Gson = GsonBuilder()
        .setStrictness(Strictness.LENIENT)
        .create()
    
    /**
     * Initialize settings from assets
     * Should be called once at app startup (e.g., in Application.onCreate())
     * 
     * @param context Application context
     * @return True if loaded successfully, false if using defaults
     */
    fun initialize(context: Context): Boolean {
        return try {
            val jsonString = context.assets.open(SETTINGS_FILE).bufferedReader().use { it.readText() }
            _settings = gson.fromJson(jsonString, AppSettings::class.java)
            
            Log.d(TAG, "Settings loaded successfully: v${settings.version}")
            Log.d(TAG, "  Hysteresis: ${settings.angleDetection.hysteresisDegrees}°")
            Log.d(TAG, "  Boundary Buffer: ${settings.angleDetection.boundaryBufferDegrees}°")
            Log.d(TAG, "  Smoothing Window: ${settings.movementDetection.smoothingWindowSize}")
            
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load settings, using defaults: ${e.message}")
            _settings = AppSettings()
            false
        }
    }
    
    /**
     * Reload settings (useful for debugging or hot reload)
     */
    fun reload(context: Context): Boolean {
        _settings = null
        return initialize(context)
    }
    
    // ==================== Convenience Accessors ====================
    
    /**
     * Get hysteresis degrees for phase transitions
     */
    fun getHysteresis(): Double = settings.angleDetection.hysteresisDegrees
    
    /**
     * Get boundary buffer for validation tolerance
     */
    fun getBoundaryBuffer(): Double = settings.angleDetection.boundaryBufferDegrees
    
    /**
     * Get extreme error threshold
     */
    fun getExtremeErrorThreshold(): Double = settings.angleDetection.extremeErrorThresholdDegrees
    
    /**
     * Get smoothing window size
     */
    fun getSmoothingWindowSize(): Int = settings.movementDetection.smoothingWindowSize
    
    /**
     * Get default min rep interval
     */
    fun getDefaultMinRepInterval(): Long = settings.defaults.minRepIntervalMs
    
    /**
     * Get default max rep interval
     */
    fun getDefaultMaxRepInterval(): Long = settings.defaults.maxRepIntervalMs
    
    /**
     * Get default min phase duration
     */
    fun getDefaultMinPhaseDuration(): Long = settings.defaults.minPhaseDurationMs
    
    // ==================== Hold Defaults ====================
    
    /**
     * Get default hold duration in seconds
     */
    fun getDefaultHoldDuration(): Int = settings.holdDefaults.defaultDurationSeconds
    
    /**
     * Get default grace period in milliseconds for hold exercises
     */
    fun getDefaultGracePeriod(): Long = settings.holdDefaults.defaultGracePeriodMs
}
