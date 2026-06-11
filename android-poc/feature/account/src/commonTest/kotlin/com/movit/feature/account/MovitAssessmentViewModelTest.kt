package com.movit.feature.account

import com.movit.core.training.model.JointAngles
import com.movit.core.training.model.Landmark
import com.movit.core.training.model.PoseFrame
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovitAssessmentViewModelTest {

    @Test
    fun initialState_startsAtPreScreeningWithDefaultNoAnswers() {
        val viewModel = MovitAssessmentViewModel()
        val state = viewModel.state.value
        assertEquals(AssessmentPhase.PreScreening, state.phase)
        assertEquals(AssessmentDefaults.parqQuestions.size, state.parqAnswers.size)
        assertTrue(state.parqAnswers.values.all { !it })
    }

    @Test
    fun parqAnswered_updatesAnswerMap() {
        val viewModel = MovitAssessmentViewModel()
        viewModel.onEvent(MovitAssessmentEvent.ParqAnswered(0, true))
        assertEquals(true, viewModel.state.value.parqAnswers[0])
    }

    @Test
    fun continueToBodyScan_advancesPhase() {
        val viewModel = MovitAssessmentViewModel()
        viewModel.onEvent(MovitAssessmentEvent.ContinueToBodyScan)
        assertEquals(AssessmentPhase.BodyScan, viewModel.state.value.phase)
    }

    @Test
    fun continueToBodyScan_withYesAnswer_emitsPhysicianWarning() {
        runBlocking {
            val viewModel = MovitAssessmentViewModel()
            val effectDeferred = async {
                withTimeout(5_000) { viewModel.effects.first() }
            }
            yield()
            viewModel.onEvent(MovitAssessmentEvent.ParqAnswered(1, true))
            viewModel.onEvent(MovitAssessmentEvent.ContinueToBodyScan)

            val effect = effectDeferred.await()
            assertTrue(effect is MovitAssessmentEffect.ShowLocalizedMessage)
            assertEquals("assessment_parq_physician_warning", effect.key)
        }
    }

    @Test
    fun completeBodyScan_loadsResultsFromRepository() {
        runBlocking {
            val customResults = FakeAssessmentPreviewData.results.copy(bodyScore = 81)
            val viewModel = MovitAssessmentViewModel(
                repository = FakeAssessmentRepository(results = customResults),
            )
            viewModel.onEvent(MovitAssessmentEvent.ContinueToBodyScan)
            repeat(AssessmentDefaults.initialTemplate.movements.size * 24) { index ->
                viewModel.onEvent(MovitAssessmentEvent.BodyScanFrameReceived(testPoseFrame(index.toLong())))
            }
            viewModel.onEvent(MovitAssessmentEvent.CompleteBodyScan)

            val state = withTimeout(5_000) {
                viewModel.state
                    .filter { it.phase == AssessmentPhase.Results && !it.isLoadingResults }
                    .first()
            }
            assertEquals(81, state.results.bodyScore)
        }
    }

    @Test
    fun backFromBodyScan_returnsToPreScreening() {
        val viewModel = MovitAssessmentViewModel()
        viewModel.onEvent(MovitAssessmentEvent.ContinueToBodyScan)
        viewModel.onEvent(MovitAssessmentEvent.BackClicked)
        assertEquals(AssessmentPhase.PreScreening, viewModel.state.value.phase)
    }

    @Test
    fun bodyScanDefaults_secondMovementOfThree() {
        val viewModel = MovitAssessmentViewModel()
        viewModel.onEvent(MovitAssessmentEvent.ContinueToBodyScan)
        assertEquals(1, viewModel.state.value.scanMovementNumber)
        assertEquals(3, viewModel.state.value.scanMovementTotal)
    }

    @Test
    fun bodyScanFrames_markScanComplete() {
        val viewModel = MovitAssessmentViewModel()
        viewModel.onEvent(MovitAssessmentEvent.ContinueToBodyScan)

        repeat(AssessmentDefaults.initialTemplate.movements.size * 24) { index ->
            viewModel.onEvent(MovitAssessmentEvent.BodyScanFrameReceived(testPoseFrame(index.toLong())))
        }

        assertTrue(viewModel.state.value.isScanComplete)
        assertEquals(100, viewModel.state.value.scanProgressPercent)
    }
}

private fun testPoseFrame(timestamp: Long): PoseFrame = PoseFrame(
    angles = JointAngles(
        leftShoulder = 150.0,
        rightShoulder = 148.0,
        leftHip = 105.0,
        rightHip = 103.0,
        leftKnee = 132.0,
        rightKnee = 130.0,
        leftAnkle = 92.0,
        rightAnkle = 91.0,
    ),
    landmarks = List(33) { Landmark(x = 0.5f, y = 0.5f, z = 0f, visibility = 0.92f, presence = 0.95f) },
    isFrontCamera = true,
    timestampMs = timestamp,
)
