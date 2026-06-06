package com.trainingvalidator.poc.training.workout

/**
 * ReportAggregator — UI utility helpers for formatting report data.
 *
 * NOTE: Aggregation logic (Day/Week/Program calculations) has been moved
 * to the backend unified reports endpoint (GET /api/mobile/reports/metrics).
 * The mobile app should use [com.trainingvalidator.poc.storage.ReportRepository]
 * for all metric calculations. This object only provides display helpers.
 */
object ReportAggregator {

    // ═══════════════════════════════════════════════════════════
    // Form Score Rating — human-friendly labels
    // ═══════════════════════════════════════════════════════════

    enum class FormRating(val label: String) {
        EXCELLENT("Excellent"),
        GOOD("Good"),
        SOLID("Solid"),
        KEEP_PRACTICING("Keep Practicing");
    }

    fun getFormRating(formScore: Float): FormRating {
        return when {
            formScore >= 85f -> FormRating.EXCELLENT
            formScore >= 70f -> FormRating.GOOD
            formScore >= 50f -> FormRating.SOLID
            else -> FormRating.KEEP_PRACTICING
        }
    }

    /**
     * Format duration (ms) as human-friendly string.
     * e.g. 3600000 -> "1h 0m", 150000 -> "2m"
     */
    fun formatDuration(durationMs: Long): String {
        val safeMs = durationMs.coerceAtLeast(0L)
        val totalMinutes = safeMs / 60000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
