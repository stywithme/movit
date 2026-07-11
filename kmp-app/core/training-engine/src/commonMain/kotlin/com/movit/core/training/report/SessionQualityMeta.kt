package com.movit.core.training.report

import kotlinx.serialization.Serializable

/**
 * Session capture confidence (I-20) — computed from motion journal frame stats when available.
 */
@Serializable
data class SessionQualityMeta(
    val framesOffered: Int = 0,
    val framesRecorded: Int = 0,
    val framesDropped: Int = 0,
    /** 0–100 percentage of offered frames that were dropped before recording. */
    val frameDropRate: Float = 0f,
    /** 0–1 ratio of joints with valid angle samples in recorded frames. */
    val jointCoverageRatio: Float? = null,
    val visibilityPauseCount: Int = 0,
    val cameraWarningCount: Int = 0,
    /** WP-19 / INNOV-6: actual camera throughput profile id. */
    val throughputProfileId: String? = null,
    /** Achieved analysis fps estimate for the session (nullable until measured). */
    val avgAchievedFps: Float? = null,
    /** Count of adaptive throughput step-downs during the session. */
    val adaptiveDowngrades: Int = 0,
) {
    companion object {
        fun fromFrameStats(
            framesOffered: Int,
            framesRecorded: Int,
            framesDropped: Int,
            jointCoverageRatio: Float?,
            visibilityPauseCount: Int = 0,
            cameraWarningCount: Int = 0,
            throughputProfileId: String? = null,
            avgAchievedFps: Float? = null,
            adaptiveDowngrades: Int = 0,
        ): SessionQualityMeta {
            val dropRate = if (framesOffered > 0) {
                (framesDropped.toFloat() / framesOffered.toFloat()) * 100f
            } else {
                0f
            }
            return SessionQualityMeta(
                framesOffered = framesOffered,
                framesRecorded = framesRecorded,
                framesDropped = framesDropped,
                frameDropRate = dropRate,
                jointCoverageRatio = jointCoverageRatio,
                visibilityPauseCount = visibilityPauseCount,
                cameraWarningCount = cameraWarningCount,
                throughputProfileId = throughputProfileId,
                avgAchievedFps = avgAchievedFps,
                adaptiveDowngrades = adaptiveDowngrades,
            )
        }
    }
}
