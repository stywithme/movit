package com.movit.core.training.diagnostics

import com.movit.core.training.session.SessionRunState
import kotlin.test.Test
import kotlin.test.assertTrue

class TrainingPipelineDiagnosticsTest {
    @Test
    fun periodicLine_includesAllPipelineStages() {
        val line = formatTrainingPipelinePeriodicForTest(
            cameraAccepted = 10,
            cameraSkipped = 4,
            cameraTargetFps = 10,
            cameraAnalysisSize = "320x240",
            cameraAppliedFps = "[10,10]",
            cameraThroughputProfile = "stable",
            poseWithBody = 7,
            poseNoBody = 1,
            avgInferenceMs = 44,
            poseBusySkipped = 2,
            poseStallEvents = 0,
            vmIngress = 8,
            vmProcessed = 8,
            vmConflated = 0,
            runState = SessionRunState.TRAINING,
            phase = "BOTTOM",
            repCount = 3,
            targetReps = 12,
            formScore = 95,
            droppedEngine = 0,
            droppedSupervisor = 0,
        )
        assertTrue("cam=10fps" in line)
        assertTrue("profile=stable" in line)
        assertTrue("analysis=320x240" in line)
        assertTrue("pose=8fps" in line)
        assertTrue("inferMs=44" in line)
        assertTrue("vm=in 8" in line)
        assertTrue("reps=3/12" in line)
        assertTrue("supervisor=drop=0" in line)
    }
}
