package com.movit.core.training.engine

/**
 * Quality level of a joint's current angle position.
 * Ported from legacy Android training models for KMP sharing.
 */
enum class JointState {
    PERFECT,
    NORMAL,
    PAD,
    WARNING,
    DANGER,
    TRANSITION;

    val priority: Int
        get() = when (this) {
            DANGER -> 5
            WARNING -> 4
            PAD -> 3
            NORMAL -> 2
            PERFECT -> 1
            TRANSITION -> 0
        }

    fun isWorseThan(other: JointState): Boolean = priority > other.priority

    fun isBetterThan(other: JointState): Boolean = priority < other.priority

    val isScorableForRepQuality: Boolean
        get() = this == PERFECT || this == NORMAL || this == PAD

    companion object {
        fun getWorst(states: Collection<JointState>): JointState =
            states.maxByOrNull { it.priority } ?: PERFECT
    }
}

enum class ZoneType {
    UP_ZONE,
    DOWN_ZONE,
    TRANSITION,
}
