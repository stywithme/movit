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
    
    @Volatile
    private var _settings: AppSettings? = null
    
    // Default settings instance (created once, reused if not initialized)
    private val defaultSettings = AppSettings()
    
    /**
     * Current app settings (cached)
     * Thread-safe: uses volatile field and returns immutable data class
     * 
     * Note: Returns default settings if not initialized. This is safe but
     * may not reflect user's app_settings.json configuration.
     */
    val settings: AppSettings
        get() {
            val current = _settings
            if (current == null) {
                Log.w(TAG, "SettingsManager accessed before initialization - using defaults")
                return defaultSettings
            }
            return current
        }
    
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
        // Initialize SharedPreferences for runtime settings
        runtimePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        return try {
            val jsonString = context.assets.open(SETTINGS_FILE).bufferedReader().use { it.readText() }
            _settings = gson.fromJson(jsonString, AppSettings::class.java)
            
            Log.d(TAG, "Settings loaded successfully: v${settings.version}")
            Log.d(TAG, "  Hysteresis: ${settings.angleDetection.hysteresisDegrees}°")
            Log.d(TAG, "  Boundary Buffer: ${settings.angleDetection.boundaryBufferDegrees}°")
            Log.d(TAG, "  Smoothing Window: ${settings.movementDetection.smoothingWindowSize}")
            Log.d(TAG, "  Smoothing Settings:")
            Log.d(TAG, "    Preset: ${settings.smoothing.preset}")
            Log.d(TAG, "    Use Legacy EMA: ${settings.smoothing.useLegacyEMA}")
            Log.d(TAG, "    MinCutoff: ${settings.smoothing.minCutoff}")
            Log.d(TAG, "    Beta: ${settings.smoothing.beta}")
            Log.d(TAG, "    Legacy Alpha: ${settings.smoothing.legacyAlpha}")
            
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
     * Get smoothing window size
     */
    fun getSmoothingWindowSize(): Int = settings.movementDetection.smoothingWindowSize
    
    /**
     * Get default min rep interval
     */
    fun getDefaultMinRepInterval(): Long = settings.defaults.minRepIntervalMs
    
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
    
    // ==================== Smoothing Settings ====================
    
    /**
     * Get smoothing settings object
     */
    fun getSmoothingSettings(): SmoothingSettings = settings.smoothing
    
    /**
     * Get smoothing preset name
     * @return One of: "responsive", "balanced", "smooth", "custom"
     */
    fun getSmoothingPreset(): String = settings.smoothing.preset
    
    /**
     * Get One Euro Filter minCutoff parameter
     * Lower = smoother but more lag
     */
    fun getSmoothingMinCutoff(): Float {
        return when (settings.smoothing.preset) {
            "responsive" -> 2.5f
            "balanced" -> 1.5f
            "smooth" -> 0.8f
            else -> settings.smoothing.minCutoff
        }
    }
    
    /**
     * Get One Euro Filter beta parameter
     * Higher = more responsive to fast movements
     */
    fun getSmoothingBeta(): Float {
        return when (settings.smoothing.preset) {
            "responsive" -> 0.02f
            "balanced" -> 0.01f
            "smooth" -> 0.005f
            else -> settings.smoothing.beta
        }
    }
    
    /**
     * Whether to use legacy EMA instead of One Euro Filter
     */
    fun useLegacySmoothing(): Boolean = settings.smoothing.useLegacyEMA
    
    /**
     * Get legacy EMA alpha value
     */
    fun getLegacySmoothingAlpha(): Float = settings.smoothing.legacyAlpha
    
    // ==================== Visibility Settings ====================
    
    /**
     * Get visibility threshold for skeleton overlay drawing
     */
    fun getOverlayVisibility(): Float = settings.visibility.overlay
    
    /**
     * Get visibility threshold for pose validation
     */
    fun getPoseValidationVisibility(): Float = settings.visibility.poseValidation
    
    // ==================== Pose Validation Settings ====================
    
    /**
     * Check if an angle value is within valid anatomical range
     */
    fun isAngleValid(angle: Double): Boolean {
        return angle >= settings.poseValidation.minValidAngle && 
               angle <= settings.poseValidation.maxValidAngle
    }
    
    // ==================== Overlay Opacity Settings ====================
    
    /**
     * Get opacity for non-tracked joints
     */
    fun getNonTrackedOpacity(): Float = settings.overlayOpacity.nonTracked
    
    /**
     * Get opacity for tracked joints in correct position
     */
    fun getTrackedCorrectOpacity(): Float = settings.overlayOpacity.trackedCorrect
    
    /**
     * Get opacity for tracked joints in error position
     */
    fun getTrackedErrorOpacity(): Float = settings.overlayOpacity.trackedError
    
    // ==================== Range Indicator Type ====================
    
    /**
     * Get range indicator type settings
     */
    fun getRangeIndicatorSettings(): RangeIndicatorSettings = settings.rangeIndicator
    
    /**
     * Get indicator type: "line" or "arc"
     * Returns runtime value if set, otherwise from config
     */
    fun getIndicatorType(): String {
        return runtimePrefs?.getString(KEY_INDICATOR_TYPE, null) 
            ?: settings.rangeIndicator.type
    }
    
    /**
     * Check if Arc indicator should be used
     * Reads from runtime settings first, then falls back to config
     */
    fun useArcIndicator(): Boolean = getIndicatorType() == "arc"
    
    /**
     * Check if Line indicator should be used
     * Reads from runtime settings first, then falls back to config
     */
    fun useLineIndicator(): Boolean = getIndicatorType() == "line"
    
    // ==================== Line Indicator Settings ====================
    
    /**
     * Get line indicator settings object
     */
    fun getLineIndicatorSettings(): LineIndicatorSettings = settings.lineIndicator
    
    /**
     * Get center angle (typically 90°)
     */
    fun getLineIndicatorCenterAngle(): Double = settings.lineIndicator.centerAngle
    
    /**
     * Get smoothing factor for line movement
     */
    fun getLineIndicatorSmoothingFactor(): Float = settings.lineIndicator.smoothingFactor
    
    /**
     * Get snap to zero threshold
     */
    fun getLineIndicatorSnapThreshold(): Float = settings.lineIndicator.snapToZeroThreshold
    
    /**
     * Get track alpha (0-255)
     */
    fun getLineIndicatorTrackAlpha(): Int = settings.lineIndicator.track.alpha
    
    /**
     * Get track width ratio
     */
    fun getLineIndicatorTrackWidthRatio(): Float = settings.lineIndicator.track.widthRatio
    
    /**
     * Get indicator width multiplier
     */
    fun getLineIndicatorWidthMultiplier(): Float = settings.lineIndicator.indicator.widthMultiplier
    
    /**
     * Get upper limb length ratio
     */
    fun getLineIndicatorUpperLengthRatio(): Float = settings.lineIndicator.lengthRatio.upper
    
    /**
     * Get lower limb length ratio
     */
    fun getLineIndicatorLowerLengthRatio(): Float = settings.lineIndicator.lengthRatio.lower
    
    /**
     * Get stroke width for normal state
     */
    fun getLineIndicatorStrokeWidthNormal(): Float = settings.lineIndicator.strokeWidth.normal
    
    /**
     * Get stroke width for warning state
     */
    fun getLineIndicatorStrokeWidthWarning(): Float = settings.lineIndicator.strokeWidth.warning
    
    /**
     * Get stroke width for error state
     */
    fun getLineIndicatorStrokeWidthError(): Float = settings.lineIndicator.strokeWidth.error
    
    /**
     * Get joint radius for normal state
     */
    fun getLineIndicatorJointRadiusNormal(): Float = settings.lineIndicator.jointRadius.normal
    
    /**
     * Get joint radius for warning state
     */
    fun getLineIndicatorJointRadiusWarning(): Float = settings.lineIndicator.jointRadius.warning
    
    /**
     * Get joint radius for error state
     */
    fun getLineIndicatorJointRadiusError(): Float = settings.lineIndicator.jointRadius.error
    
    // ==================== Feedback Settings ====================
    
    /**
     * Get feedback settings object
     */
    fun getFeedbackSettings(): FeedbackSettings = settings.feedback
    
    /**
     * Get state message cooldown in milliseconds
     */
    fun getStateMessageCooldown(): Long = settings.feedback.stateMessageCooldownMs
    
    /**
     * Get random message idle time in milliseconds
     */
    fun getRandomMessageIdleTime(): Long = settings.feedback.randomMessageIdleMs
    
    /**
     * Get random message cooldown in milliseconds
     */
    fun getRandomMessageCooldown(): Long = settings.feedback.randomMessageCooldownMs
    
    // ==================== Runtime Settings (SharedPreferences) ====================
    
    private var runtimePrefs: android.content.SharedPreferences? = null
    
    private const val PREFS_NAME = "training_settings"
    private const val KEY_INDICATOR_TYPE = "indicator_type"
    private const val KEY_VOICE_FEEDBACK_ENABLED = "voice_feedback_enabled"
    private const val KEY_MODEL_TYPE = "model_type"
    private const val KEY_TRAINING_DISPLAY_MODE = "training_display_mode"
    
    /**
     * Initialize SharedPreferences for runtime settings
     */
    private fun getPrefs(context: Context? = null): android.content.SharedPreferences? {
        if (runtimePrefs == null && context != null) {
            runtimePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
        return runtimePrefs
    }
    
    /**
     * Set indicator type at runtime ("line" or "arc")
     */
    fun setIndicatorType(type: String) {
        runtimePrefs?.edit()?.putString(KEY_INDICATOR_TYPE, type)?.apply()
    }
    
    /**
     * Check if voice feedback is enabled
     */
    fun isVoiceFeedbackEnabled(): Boolean {
        return runtimePrefs?.getBoolean(KEY_VOICE_FEEDBACK_ENABLED, true) ?: true
    }
    
    /**
     * Set voice feedback enabled/disabled
     */
    fun setVoiceFeedbackEnabled(enabled: Boolean) {
        runtimePrefs?.edit()?.putBoolean(KEY_VOICE_FEEDBACK_ENABLED, enabled)?.apply()
    }
    
    /**
     * Get MediaPipe model type ("full" or "heavy")
     */
    fun getModelType(): String {
        return runtimePrefs?.getString(KEY_MODEL_TYPE, "full") ?: "full"
    }
    
    /**
     * Set MediaPipe model type
     */
    fun setModelType(type: String) {
        runtimePrefs?.edit()?.putString(KEY_MODEL_TYPE, type)?.apply()
    }

    /**
     * Get training display mode: "beginner" or "advanced".
     */
    fun getTrainingDisplayMode(): String {
        return runtimePrefs?.getString(KEY_TRAINING_DISPLAY_MODE, "beginner") ?: "beginner"
    }

    /**
     * Set training display mode at runtime.
     */
    fun setTrainingDisplayMode(mode: String) {
        val normalized = if (mode == "advanced") "advanced" else "beginner"
        runtimePrefs?.edit()?.putString(KEY_TRAINING_DISPLAY_MODE, normalized)?.apply()
    }

    fun isAdvancedTrainingDisplay(): Boolean = getTrainingDisplayMode() == "advanced"
}
