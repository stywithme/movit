package com.movit.feature.account

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
        assertEquals(FakeAssessmentPreviewData.parqQuestions.size, state.parqAnswers.size)
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
        assertEquals(2, viewModel.state.value.scanMovementNumber)
        assertEquals(3, viewModel.state.value.scanMovementTotal)
    }
}
