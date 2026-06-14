package com.movit.feature.training

/**
 * Planned workout write contract (backend + legacy parity).
 *
 * - `POST …/complete` → marks report completed, updates program progress, runs progression.
 * - `POST …/report` → legacy partial metric update only; does **not** complete the day.
 *
 * Legacy [SyncManager.flushPendingWorkoutReports] calls `/complete` exclusively.
 * New clients must **not** call both on the same session — that duplicates outbox writes
 * and can leave progress in an inconsistent state if `/report` runs after `/complete`.
 */
object TrainingSessionPlannedWritePolicy {
    const val CANONICAL_COMPLETE_ENDPOINT = "complete"

    /** Legacy `/report` is retained for old APKs only — not paired with `/complete`. */
    fun shouldEnqueueLegacyReportAfterComplete(): Boolean = false
}
