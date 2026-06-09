package com.movit.core.training.engine

enum class CountingMethod {
    UP_DOWN,
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
