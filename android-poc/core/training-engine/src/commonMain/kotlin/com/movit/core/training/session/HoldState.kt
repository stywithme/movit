package com.movit.core.training.session

enum class HoldState {
    IDLE,
    HOLDING,
    GRACE_PERIOD,
    COMPLETED,
    FAILED,
}
