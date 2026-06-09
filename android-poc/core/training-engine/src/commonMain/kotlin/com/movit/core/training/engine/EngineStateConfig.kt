package com.movit.core.training.engine

internal object EngineStateConfig {
    fun isRepCounted(state: JointState): Boolean = when (state) {
        JointState.PERFECT, JointState.NORMAL, JointState.PAD -> true
        else -> false
    }

    fun invalidatesRep(state: JointState): Boolean = state == JointState.DANGER
}
