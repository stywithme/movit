package com.movit.core.data.repository

import com.movit.core.data.local.InMemoryMovitLocalStore
import com.movit.core.training.config.LocalizedText
import com.movit.core.training.report.MovitExecutionQuality
import com.movit.core.training.report.MovitPerformanceRating
import com.movit.core.training.report.MovitPerformanceSummary
import com.movit.core.training.report.MovitPostTrainingReport
import com.movit.core.training.report.MovitStateBreakdown
import com.movit.core.training.report.MovitTrackingQualityLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PostTrainingReportLocalStoreTest {

    @Test
    fun putAndGet_roundTripsPostTrainingReport() {
        val local = InMemoryMovitLocalStore()
        val store = PostTrainingReportLocalStore(local)
        val report = samplePostTrainingReport("upload-42")

        store.putPostTraining("upload-42", report)

        assertEquals(report, store.getPostTraining("upload-42"))
        assertEquals(report, PostTrainingReportLocalStore(local).getPostTraining("upload-42"))
    }

    @Test
    fun registerExerciseSetReport_indexesReportsBySessionExercise() {
        val local = InMemoryMovitLocalStore()
        val store = PostTrainingReportLocalStore(local)
        val sessionKey = "session:squat"

        store.putPostTraining("upload-set-1", samplePostTrainingReport("upload-set-1"))
        store.putPostTraining("upload-set-2", samplePostTrainingReport("upload-set-2"))
        store.registerExerciseSetReport(sessionKey, setNumber = 1, reportId = "upload-set-1")
        store.registerExerciseSetReport(sessionKey, setNumber = 2, reportId = "upload-set-2")

        assertEquals(sessionKey, store.getReportSessionExerciseKey("upload-set-2"))
        assertEquals(
            mapOf(1 to "upload-set-1", 2 to "upload-set-2"),
            store.listExerciseSetReportIds(sessionKey),
        )
    }

    @Test
    fun rekeyPostTraining_movesReportToNewId() {
        val local = InMemoryMovitLocalStore()
        val store = PostTrainingReportLocalStore(local)
        store.putPostTraining("local-upload", samplePostTrainingReport("local-upload"))

        store.rekeyPostTraining("local-upload", "server-report-42")

        assertNull(store.getPostTraining("local-upload"))
        assertEquals("server-report-42", store.getPostTraining("server-report-42")?.id)
    }

    private fun samplePostTrainingReport(uploadId: String): MovitPostTrainingReport = MovitPostTrainingReport(
        id = uploadId,
        workoutId = uploadId,
        exerciseId = "squat",
        exerciseName = LocalizedText(en = "Squat", ar = "قرفصاء"),
        timestamp = 1L,
        summary = MovitPerformanceSummary(
            totalReps = 8,
            durationMs = 60_000L,
            rating = MovitPerformanceRating.GOOD,
            motivationalMessage = LocalizedText(en = "Good", ar = "جيد"),
            countedReps = 8,
            invalidatedReps = 0,
            averageScore = 88f,
            countedRatio = 1f,
            stateBreakdown = MovitStateBreakdown(normalCount = 8),
            shouldCelebrate = false,
        ),
        executionQuality = MovitExecutionQuality(
            visibilityPauseCount = 0,
            totalInvisibleMs = 0,
            cameraWarningCount = 0,
            overallQuality = MovitTrackingQualityLevel.GOOD,
        ),
    )
}
