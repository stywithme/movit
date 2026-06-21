package com.movit.core.training.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CountingMethod {
    @SerialName("up_down")
    UP_DOWN,

    @SerialName("hold")
    HOLD,
}

enum class Phase {
    IDLE,
    START,
    DOWN,
    BOTTOM,
    UP,
    COUNT,
}

enum class RepIncompleteReason {
    NO_TARGET_DEPTH,
    NO_FULL_RETURN,
    TOO_FAST,
    TOO_SLOW,
}
