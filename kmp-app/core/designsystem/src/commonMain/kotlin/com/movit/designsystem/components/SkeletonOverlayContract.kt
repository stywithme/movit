package com.movit.designsystem.components

/**
 * Parity contract for live training / setup skeleton overlays (Legacy [SkeletonOverlayView]).
 *
 * Wiring: [com.movit.feature.training.buildSkeletonOverlayParityState] → [MovitSkeletonOverlay].
 */
enum class SkeletonOverlayMode {
    /** Live training: ROM indicators only; setup/preview own the full skeleton drawing. */
    TRAINING,
    /** Setup ANGLES phase: coloured joints, bone lines, direction arrows. */
    SETUP_ANGLES,
    /** Setup REGION/POSTURE/DIRECTION: intentional blank overlay over camera preview. */
    SCENE_CHECK,
    /** Countdown / idle: basic skeleton only (connections + faint joints). */
    PREVIEW,
}

enum class SkeletonPositionSeverity {
    ERROR,
    WARNING,
    TIP,
}

/** Bracket-style position check mark between two landmark indices (analysis space). */
data class SkeletonPositionErrorMark(
    val landmark1Index: Int,
    val landmark2Index: Int,
    val severity: SkeletonPositionSeverity,
)

data class SkeletonSetupJointHighlight(
    val jointCode: String,
    val level: String,
    val currentAngleDeg: Float? = null,
    val direction: SkeletonSetupDirection? = null,
    val isPrimary: Boolean = true,
)

enum class SkeletonSetupDirection {
    RAISE,
    LOWER,
    HOLD,
}

/** Small on-screen label for bilateral side switching (Legacy flip + user orientation). */
data class SkeletonBilateralSideHint(
    val sideCode: String,
    val label: String,
)

data class SkeletonOverlayParityState(
    val mode: SkeletonOverlayMode = SkeletonOverlayMode.PREVIEW,
    val jointVisuals: Map<String, SkeletonJointVisual> = emptyMap(),
    val positionErrors: List<SkeletonPositionErrorMark> = emptyList(),
    val setupHighlights: List<SkeletonSetupJointHighlight> = emptyList(),
    val bilateralSideHint: SkeletonBilateralSideHint? = null,
    /** When true, joint/ROM landmark lookup mirrors left↔right (config side vs visible anatomy). */
    val isBilateralFlipped: Boolean = false,
)
