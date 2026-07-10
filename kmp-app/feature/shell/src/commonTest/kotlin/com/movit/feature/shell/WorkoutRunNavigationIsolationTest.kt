package com.movit.feature.shell

import com.movit.feature.library.ExerciseTarget
import com.movit.feature.library.ReturnTarget
import com.movit.feature.library.WorkoutRunBlock
import com.movit.feature.library.WorkoutRunSnapshot
import com.movit.feature.library.WorkoutRunSource
import com.movit.feature.library.WorkoutRunStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorkoutRunNavigationIsolationTest {

    private fun sampleSnapshot(workoutId: String) = WorkoutRunSnapshot(
        workoutId = workoutId,
        title = "Test",
        blocks = listOf(
            WorkoutRunBlock.Exercise(
                exerciseId = "ex-1",
                slug = "squat",
                displayName = "Squat",
                phaseRole = "MAIN",
                target = ExerciseTarget.Reps(10),
                sets = 2,
                restBetweenSetsMs = 30_000,
                restAfterExerciseMs = 60_000,
                poseVariantIndex = 0,
                weightPerSetKg = null,
            ),
        ),
    )

    @Test
    fun replaceWorkoutJourneyWithReport_dropsPrepareAndTraining() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutSession("w-a")),
        )
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(
                MovitInnerRoute.ExercisePrepare(exerciseId = "ex-1", workoutId = "w-a"),
            ),
        )
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(
                MovitInnerRoute.TrainingSession(
                    exerciseSlug = "squat",
                    exerciseName = "Squat",
                    targetReps = 10,
                    workoutId = "w-a",
                    runId = "run-1",
                ),
            ),
        )

        viewModel.onEvent(
            MovitAppShellEvent.ReplaceWorkoutJourneyWithReport(
                reportId = "run-1",
                returnTarget = ReturnTarget.WorkoutSession("w-a"),
                doneTarget = ReturnTarget.Explore,
            ),
        )

        val stack = viewModel.state.value.innerStack
        assertEquals(2, stack.size)
        assertIs<MovitInnerRoute.WorkoutSession>(stack[0])
        val report = assertIs<MovitInnerRoute.ReportDetail>(stack[1])
        assertEquals("run-1", report.reportId)
        assertEquals(ReturnTarget.WorkoutSession("w-a"), report.returnTarget)
        assertEquals(ReturnTarget.Explore, report.doneTarget)
    }

    @Test
    fun backFromReport_doesNotReopenTrainingSession() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutSession("w-a")),
        )
        viewModel.onEvent(
            MovitAppShellEvent.ReplaceWorkoutJourneyWithReport(
                reportId = "report-a",
                returnTarget = ReturnTarget.WorkoutSession("w-a"),
                doneTarget = ReturnTarget.Explore,
            ),
        )
        viewModel.onEvent(MovitAppShellEvent.InnerRoutePopped)

        val session = assertIs<MovitInnerRoute.WorkoutSession>(viewModel.state.value.currentInnerRoute)
        assertEquals("w-a", session.workoutId)
        assertTrue(viewModel.state.value.innerStack.none { it is MovitInnerRoute.TrainingSession })
        assertTrue(viewModel.state.value.innerStack.none { it is MovitInnerRoute.ReportDetail })
    }

    @Test
    fun openWorkoutAThenB_keepsDistinctRunRecords() {
        val runA = WorkoutRunStore.start(
            workoutId = "workout-a-nav",
            snapshot = sampleSnapshot("workout-a-nav"),
            source = WorkoutRunSource.Explore,
        )
        WorkoutRunStore.complete(runA.runId.value)
        val runB = WorkoutRunStore.start(
            workoutId = "workout-b-nav",
            snapshot = sampleSnapshot("workout-b-nav"),
            source = WorkoutRunSource.Train,
            returnTarget = ReturnTarget.Train,
            doneTarget = ReturnTarget.Train,
        )
        assertEquals("workout-a-nav", WorkoutRunStore.get(runA.runId)?.workoutId)
        assertEquals("workout-b-nav", WorkoutRunStore.get(runB.runId)?.workoutId)
        assertNotEquals(runA.runId, runB.runId)
        assertNotEquals(runA.workoutGroupId, runB.workoutGroupId)
    }

    @Test
    fun doneFromReport_clearsInnerAndSelectsTrain() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutSession("w-a")),
        )
        viewModel.onEvent(
            MovitAppShellEvent.ReplaceWorkoutJourneyWithReport(
                reportId = "report-a",
                returnTarget = ReturnTarget.WorkoutSession("w-a"),
                doneTarget = ReturnTarget.Train,
            ),
        )
        viewModel.onEvent(
            MovitAppShellEvent.NavigateReportReturn(
                target = ReturnTarget.Train,
                clearInner = true,
            ),
        )
        assertTrue(viewModel.state.value.innerStack.isEmpty())
        assertEquals(MovitAppDestination.Train, viewModel.state.value.selectedDestination)
    }

    @Test
    fun backFromProgramReport_returnsToSelectedProgramWeek() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.ProgramDetail("program-a", 2)),
        )
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutSession("session-a")),
        )
        viewModel.onEvent(
            MovitAppShellEvent.NavigateReportReturn(
                target = ReturnTarget.ProgramDetail("program-a", 2),
                clearInner = true,
            ),
        )

        val stack = viewModel.state.value.innerStack
        assertEquals(1, stack.size)
        assertEquals(MovitInnerRoute.ProgramDetail("program-a", 2), stack.single())
    }

    @Test
    fun exitWorkoutJourney_dropsPrepareAndTrainingOntoSession() {
        val viewModel = MovitAppShellViewModel()
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(MovitInnerRoute.WorkoutSession("w-a")),
        )
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(
                MovitInnerRoute.ExercisePrepare(exerciseId = "ex-1", workoutId = "w-a"),
            ),
        )
        viewModel.onEvent(
            MovitAppShellEvent.InnerRoutePushed(
                MovitInnerRoute.TrainingSession(
                    exerciseSlug = "squat",
                    exerciseName = "Squat",
                    targetReps = 10,
                    workoutId = "w-a",
                    runId = "run-1",
                ),
            ),
        )

        viewModel.onEvent(MovitAppShellEvent.ExitWorkoutJourney)

        val stack = viewModel.state.value.innerStack
        assertEquals(1, stack.size)
        val session = assertIs<MovitInnerRoute.WorkoutSession>(stack[0])
        assertEquals("w-a", session.workoutId)
        assertTrue(stack.none { it is MovitInnerRoute.TrainingSession })
        assertTrue(stack.none { it is MovitInnerRoute.ExercisePrepare })
    }
}
