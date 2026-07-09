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

/** Phases where rep motion / scoring state is accumulated. */
val MOVEMENT_TRACKING_PHASES: Set<Phase> = setOf(Phase.DOWN, Phase.BOTTOM, Phase.UP, Phase.COUNT)

fun Map<Phase, Long>.toMovementPhaseTimings(): Map<String, Long> =
    filterKeys { it in MOVEMENT_TRACKING_PHASES }
        .mapKeys { (phase, _) -> phase.name.lowercase() }

fun shouldDiscardRepAttemptOnIncomplete(reason: RepIncompleteReason): Boolean = when (reason) {
    RepIncompleteReason.NO_TARGET_DEPTH,
    RepIncompleteReason.TOO_FAST,
    -> true
    RepIncompleteReason.NO_FULL_RETURN,
    RepIncompleteReason.TOO_SLOW,
    -> false
}
