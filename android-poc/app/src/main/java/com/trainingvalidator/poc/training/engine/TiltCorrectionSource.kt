package com.trainingvalidator.poc.training.engine

/**
 * Read-only source for screen-plane tilt correction.
 *
 * Implementations may be Android sensor backed, test fakes, or disabled sources.
 * Engine code only reads this contract and never owns Android sensor lifecycle.
 */
interface TiltCorrectionSource {
    val isAvailable: Boolean
    val correctionRadians: Float
    val rollDegrees: Float
}

/**
 * Lifecycle contract for sources that consume device resources while active.
 */
interface AcquirableTiltSource : TiltCorrectionSource {
    val isRunning: Boolean
    fun acquire(owner: String)
    fun release(owner: String)
}
