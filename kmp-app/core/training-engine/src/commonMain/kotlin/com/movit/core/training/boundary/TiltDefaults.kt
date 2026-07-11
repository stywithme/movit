package com.movit.core.training.boundary

/**
 * Documented defaults for device tilt correction (legacy DeviceTiltSettings parity).
 * Kill-switch / prefs wiring can override [enabled] later; constants stay the single source.
 */
object TiltDefaults {
    const val ENABLED: Boolean = true
    /** Legacy dead zone was 1° (KMP had drifted to 2°). */
    const val DEAD_ZONE_DEGREES: Float = 1f
    const val SMOOTHING_TAU_MS: Long = 120L
    /** ~5 Hz — enough for mount roll; saves battery vs SENSOR_DELAY_GAME. */
    const val SAMPLE_INTERVAL_MICROS: Int = 200_000
    /** iOS CoreMotion interval matching [SAMPLE_INTERVAL_MICROS]. */
    const val SAMPLE_INTERVAL_SECONDS: Double = 0.2
}
