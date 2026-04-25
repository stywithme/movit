package com.trainingvalidator.poc.training.engine.bilateral

/**
 * Active side for bilateral (alternating) exercises.
 */
enum class BilateralSide {
    LEFT,
    RIGHT;

    fun flip(): BilateralSide = if (this == LEFT) RIGHT else LEFT
}
