package com.movit.feature.training

import com.movit.core.training.config.ExerciseConfigParser
import com.movit.core.training.journal.RepMetrics
import com.movit.core.training.journal.RepMetricsData
import com.movit.core.training.journal.SessionJournalSnapshot
import com.movit.core.training.journal.StateCode
import com.movit.core.training.session.MovitTrainingEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrainingSessionWriteHooksRestoreTest {
    private val exerciseConfig = ExerciseConfigParser.parseConfigJson(
        """
        {
          "name": {"ar": "سكوات", "en": "Squat"},
          "countingMethod": "up_down",
          "poseVariants": [{
            "trackedJoints": [{
              "joint": "left_knee",
              "role": "primary",
              "startPose": {"min": 150, "max": 180},
              "upRange": {"perfect": {"min": 130, "max": 180}},
              "downRange": {"perfect": {"min": 60, "max": 100}}
            }]
          }]
        }
        """.trimIndent(),
    )

    @Test
    fun attach_withJournal_restoresRepsDurationAndSessionQuality_withoutCallingStart() {
        val recordingStartMs = 1_000L
        val journal = SessionJournalSnapshot(
            sessionId = "sess-restore",
            exerciseId = "squat",
            trackedJoints = listOf("left_knee"),
            recordingStartMs = recordingStartMs,
            completedRepMetrics = listOf(
                repMetric(1),
                repMetric(2),
                repMetric(3),
            ),
            framesOffered = 40,
            framesRecorded = 38,
            framesDropped = 2,
            jointCoverageNumerator = 30,
            jointCoverageDenominator = 40,
        )
        var lastCheckpoint: SessionJournalSnapshot? = null
        val hooks = TrainingSessionWriteHooks(
            sessionId = "sess-restore",
            exerciseSlug = "squat",
            timeProvider = { 50_000L },
            readJournal = { if (it == "sess-restore") journal else null },
            checkpointJournal = { lastCheckpoint = it },
        )
        val engine = MovitTrainingEngine(exerciseConfig = exerciseConfig)

        val restored = hooks.attach(engine, exerciseConfig)

        assertEquals(3, restored)
        assertEquals(3, lastCheckpoint?.completedRepMetrics?.size)
        assertEquals(recordingStartMs, lastCheckpoint?.recordingStartMs)
        assertEquals(40, lastCheckpoint?.framesOffered)
        assertEquals(38, lastCheckpoint?.framesRecorded)
        assertEquals(2, lastCheckpoint?.framesDropped)

        val quality = hooks.resolveSessionQualityMeta()
        assertEquals(40, quality?.framesOffered)
        assertEquals(38, quality?.framesRecorded)
        assertEquals(2, quality?.framesDropped)

        // Continue recording after restore — duration must be relative to original start.
        val endMs = recordingStartMs + 30_000L
        val upload = hooks.finalizeUpload(endTimestampMs = endMs)
        requireNotNull(upload)
        assertEquals(3, upload.totalReps)
        assertEquals(30_000, upload.durationMs)
        assertEquals(recordingStartMs, upload.timestamp)
    }

    @Test
    fun attach_withoutJournal_startsFresh() {
        var checkpointCount = 0
        val hooks = TrainingSessionWriteHooks(
            sessionId = "sess-fresh",
            exerciseSlug = "squat",
            timeProvider = { 2_000L },
            readJournal = { null },
            checkpointJournal = { checkpointCount++ },
        )
        val engine = MovitTrainingEngine(exerciseConfig = exerciseConfig)

        val restored = hooks.attach(engine, exerciseConfig)

        assertEquals(0, restored)
        assertTrue(checkpointCount >= 1)
        val upload = hooks.finalizeUpload(endTimestampMs = 5_000L)
        requireNotNull(upload)
        assertEquals(0, upload.totalReps)
        assertEquals(3_000, upload.durationMs)
        assertEquals(2_000L, upload.timestamp)
    }

    private fun repMetric(num: Int) = RepMetricsData(
        num = num,
        durationMs = 2_000,
        worstState = StateCode.NORMAL,
        score = 800,
        metrics = RepMetrics(
            rom = 600,
            symmetry = null,
            stability = 900,
            tempo = listOf(800, 200, 700),
            velocity = 45,
            formScore = 800,
            alignmentAccuracy = 900,
        ),
    )
}
