package com.movit.core.training.boundary

/**
 * Camera analysis throughput preset for live training.
 *
 * Default rollout profile is [STABLE] (320×240 @ 10fps) — do not change production
 * defaults without an explicit flag override and [TrainingPipelineDiagnostics] evidence.
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
    /** Current production-stable preset (GC/stall mitigation). */
    val STABLE = TrainingThroughputProfile(
        id = "stable",
        analysisWidth = 320,
        analysisHeight = 240,
        targetFps = 10,
    )

    /** Step-1 rollout: modest resolution + 15fps target. */
    val MEDIUM = TrainingThroughputProfile(
        id = "medium",
        analysisWidth = 480,
        analysisHeight = 360,
        targetFps = 15,
    )

    /** Step-2 rollout: 480p-class analysis + 20fps target. */
    val HIGH = TrainingThroughputProfile(
        id = "high",
        analysisWidth = 640,
        analysisHeight = 480,
        targetFps = 20,
    )

    /** Legacy MO approximate parity — internal / lab only until vetted. */
    val LEGACY_PARITY = TrainingThroughputProfile(
        id = "legacy",
        analysisWidth = 640,
        analysisHeight = 480,
        targetFps = 30,
    )

    private val byId = listOf(STABLE, MEDIUM, HIGH, LEGACY_PARITY).associateBy { it.id }

    fun resolve(profileId: String?): TrainingThroughputProfile {
        val key = profileId?.trim()?.lowercase().orEmpty()
        if (key.isEmpty()) return STABLE
        return byId[key]
            ?: when (key) {
                "boost_15" -> MEDIUM
                "boost_480" -> HIGH
                "legacy_parity" -> LEGACY_PARITY
                else -> STABLE
            }
    }

    fun toCameraConfiguration(
        profile: TrainingThroughputProfile,
        useFrontCamera: Boolean,
    ): CameraSourceConfiguration = CameraSourceConfiguration(
        useFrontCamera = useFrontCamera,
        targetFps = profile.targetFps,
        analysisWidth = profile.analysisWidth,
        analysisHeight = profile.analysisHeight,
        throughputProfileId = profile.id,
    )
}
