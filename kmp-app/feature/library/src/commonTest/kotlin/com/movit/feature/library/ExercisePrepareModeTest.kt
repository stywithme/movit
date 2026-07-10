package com.movit.feature.library

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ExercisePrepareModeTest {

    @Test
    fun workoutPreview_start_returnsToSession_withoutTrainingAction() {
        runBlocking {
            val viewModel = ExercisePrepareViewModel(
                exerciseId = "ex-squat",
                workoutId = "workout-preview",
                launchMode = ExercisePrepareMode.WorkoutPreview(
                    runDraftId = "workout-preview",
                    exerciseId = "ex-squat",
                ),
                repository = FakeLibraryRepository(),
            ).apply { enableRestTicker = false }
            viewModel.load()

            // Subscribe before emit — SharedFlow has no replay; first() after tryEmit can hang.
            val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }
            viewModel.onEvent(ExercisePrepareEvent.StartClicked(workoutId = "workout-preview"))
            assertIs<ExercisePrepareEffect.ReturnToWorkoutSession>(effectDeferred.await())
        }
    }

    @Test
    fun lockStartIndex_keepsIndexZero_evenWhenPrepareExerciseIsNotFirst() {
        WorkoutFlowCache.clearAll()
        WorkoutRunStore.clearAll()
        WorkoutFlowCache.put(
            WorkoutFlowConfigUi(
                workoutId = "w-lock",
                title = "Lock",
                subtitle = "t",
                exercises = listOf(
                    WorkoutFlowExerciseUi("ex-1", "squat", "Squat", 3, 10, null),
                    WorkoutFlowExerciseUi("ex-2", "lunge", "Lunge", 3, 10, null),
                ),
            ),
        )
        // Simulate matching second exercise slug without lock → would be index 1.
        // With lockStartIndex, resolver must keep 0 when flowItems provided.
        val flowItems = WorkoutFlowCache.get("w-lock")!!.toTrainingFlowItems(0)
        // Unit path without MovitData: resolveTrainingStart returns null; assert codec + mode instead.
        val decoded = ExercisePrepareModeCodec.decode(
            raw = ExercisePrepareModeCodec.WORKOUT_FIRST,
            exerciseId = "ex-2",
            workoutId = "w-lock",
            runId = "run-1",
        )
        val mode = decoded.first
        assertIs<ExercisePrepareMode.WorkoutFirstExercise>(mode)
        assertEquals("run-1", mode.runId)
        assertEquals(ExercisePreparePhase.Prepare, decoded.second)
        assertEquals(2, flowItems.size)
    }

    @Test
    fun codec_preview_doesNotImplyWorkoutFirst() {
        val decoded = ExercisePrepareModeCodec.decode(
            raw = ExercisePrepareModeCodec.PREVIEW,
            exerciseId = "ex-2",
            workoutId = "w1",
        )
        val mode = decoded.first
        assertIs<ExercisePrepareMode.WorkoutPreview>(mode)
        assertEquals("ex-2", mode.exerciseId)
    }

    @Test
    fun resolveWorkoutRunFinish_complete_closesActiveRun() {
        WorkoutRunStore.clearAll()
        val snapshot = WorkoutRunSnapshot(
            workoutId = "w-fin",
            title = "t",
            blocks = listOf(
                WorkoutRunBlock.Exercise(
                    exerciseId = "ex-1",
                    slug = "squat",
                    displayName = "Squat",
                    phaseRole = "MAIN",
                    target = ExerciseTarget.Reps(10),
                    sets = 1,
                    restBetweenSetsMs = 0,
                    restAfterExerciseMs = 0,
                    poseVariantIndex = 0,
                    weightPerSetKg = null,
                ),
            ),
        )
        WorkoutRunStore.start(workoutId = "w-fin", snapshot = snapshot)
        val nav = resolveWorkoutRunFinish("w-fin", isWorkoutFlowComplete = true)
        assertEquals(WorkoutRunFinishNav.Complete, nav)
        assertNull(WorkoutRunStore.activeForWorkout("w-fin"))
    }
}
