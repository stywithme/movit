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
    
    // ==================== Visual Settings ====================
    
    /**
     * Get visual settings object
     */
    fun getVisualSettings(): VisualSettings = settings.visual
    
    /**
     * Whether to show arc range indicators around tracked joints
     */
    fun getShowArcIndicators(): Boolean = settings.visual.showArcRangeIndicators
    
    /**
     * Arc indicator radius in dp
     */
    fun getArcIndicatorRadiusDp(): Float = settings.visual.arcIndicatorRadiusDp
    
    /**
     * Arc indicator stroke width in dp
     */
    fun getArcIndicatorStrokeWidthDp(): Float = settings.visual.arcIndicatorStrokeWidthDp
    
    /**
     * Whether to show current position indicator on arc
     */
    fun getArcShowCurrentIndicator(): Boolean = settings.visual.arcShowCurrentIndicator
    
    /**
     * Only show arc when joint is in error/warning state
     */
    fun getArcShowOnlyOnError(): Boolean = settings.visual.arcShowOnlyOnError
    
    /**
     * Only show arc for primary joints
     */
    fun getArcShowOnlyPrimary(): Boolean = settings.visual.arcShowOnlyPrimary
    
    /**
     * Arc opacity (0.0 - 1.0)
     */
    fun getArcOpacity(): Float = settings.visual.arcOpacity
    
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
     * Get visibility threshold for angle calculation
     * Lower = more tolerant, Higher = stricter
     */
    fun getAngleCalculationVisibility(): Float = settings.visibility.angleCalculation
    
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
     * Get required valid frames for pose confirmation
     */
    fun getRequiredValidFrames(): Int = settings.poseValidation.requiredValidFrames
    
    /**
     * Get minimum valid angle (angles below this are considered noise)
     */
    fun getMinValidAngle(): Float = settings.poseValidation.minValidAngle
    
    /**
     * Get maximum valid angle (angles above this are considered impossible)
     */
    fun getMaxValidAngle(): Float = settings.poseValidation.maxValidAngle
    
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
}
