package com.movit.core.training.boundary

/**
 * Camera analysis throughput preset for live training.
 *
 * WP-19: default is [HIGH] (640×480 @ 30fps — legacy parity). Adaptive downgrade
 * may step to [MEDIUM] then [STABLE] when p95 inference exceeds budget.
 */
data class TrainingThroughputProfile(
    val id: String,
    val analysisWidth: Int,
    val analysisHeight: Int,
    val targetFps: Int,
) {
    val analysisSizeLabel: String
        get() = "${analysisWidth}x$analysisHeight"
}

object TrainingThroughputProfiles {
    /** Low-power / thermal fallback. */
    val STABLE = TrainingThroughputProfile(
        id = "stable",
        analysisWidth = 320,
        analysisHeight = 240,
        targetFps = 10,
    )

    /** Mid tier after adaptive step-down from HIGH. */
    val MEDIUM = TrainingThroughputProfile(
        id = "medium",
        analysisWidth = 480,
        analysisHeight = 360,
        targetFps = 15,
    )

    /** Flagship default — legacy 640×480 @ 30fps. */
    val HIGH = TrainingThroughputProfile(
        id = "high",
        analysisWidth = 640,
        analysisHeight = 480,
        targetFps = 30,
    )

    /** Alias kept for flag overrides / labs. */
    val LEGACY_PARITY = TrainingThroughputProfile(
        id = "legacy",
        analysisWidth = 640,
        analysisHeight = 480,
        targetFps = 30,
    )

    private val byId = listOf(STABLE, MEDIUM, HIGH, LEGACY_PARITY).associateBy { it.id }

    /** Ordered from highest to lowest for adaptive downgrade. */
    val ADAPTIVE_LADDER: List<TrainingThroughputProfile> = listOf(HIGH, MEDIUM, STABLE)

    fun resolve(profileId: String?): TrainingThroughputProfile {
        val key = profileId?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return HIGH
        return byId[key]
            ?: when (key) {
                "boost_15" -> MEDIUM
                "boost_480", "flagship" -> HIGH
                "legacy_parity" -> LEGACY_PARITY
                else -> HIGH
            }
    }

    fun toCameraConfiguration(
        profile: TrainingThroughputProfile,
        useFrontCamera: Boolean,
        applyElbowCorrection: Boolean = true,
        collectElbowDiagnostics: Boolean = false,
    ): CameraSourceConfiguration = CameraSourceConfiguration(
        useFrontCamera = useFrontCamera,
        targetFps = profile.targetFps,
        analysisWidth = profile.analysisWidth,
        analysisHeight = profile.analysisHeight,
        throughputProfileId = profile.id,
        applyElbowCorrection = applyElbowCorrection,
        collectElbowDiagnostics = collectElbowDiagnostics,
    )

    fun stepDown(from: TrainingThroughputProfile): TrainingThroughputProfile? {
        val idx = ADAPTIVE_LADDER.indexOfFirst { it.id == from.id }
        if (idx < 0 || idx >= ADAPTIVE_LADDER.lastIndex) return null
        return ADAPTIVE_LADDER[idx + 1]
    }
}
