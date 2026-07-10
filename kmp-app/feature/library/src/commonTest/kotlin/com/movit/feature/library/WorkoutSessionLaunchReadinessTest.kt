package com.movit.feature.library

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WorkoutSessionLaunchReadinessTest {

    @Test
    fun doubleTapStart_staysLaunching_andEmitsOneStart() {
        runBlocking {
            WorkoutRunStore.clearAll()
            val viewModel = WorkoutSessionViewModel(
                workoutId = "preview",
                repository = PreviewWorkoutSessionRepository,
            )
            viewModel.load()
            assertIs<LaunchReadiness.Ready>(viewModel.state.value.launchReadiness)

            val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }
            viewModel.onEvent(WorkoutSessionEvent.StartWorkoutClicked)
            assertIs<LaunchReadiness.Launching>(viewModel.state.value.launchReadiness)

            // Further taps ignored while Launching.
            viewModel.onEvent(WorkoutSessionEvent.StartWorkoutClicked)
            viewModel.onEvent(WorkoutSessionEvent.StartWorkoutClicked)

            assertIs<WorkoutSessionEffect.StartWorkout>(effectDeferred.await())
            assertIs<LaunchReadiness.Launching>(viewModel.state.value.launchReadiness)
        }
    }

    @Test
    fun openExercise_isPreviewOnly_doesNotBeginRun() {
        runBlocking {
            WorkoutRunStore.clearAll()
            val viewModel = WorkoutSessionViewModel(
                workoutId = "preview",
                repository = PreviewWorkoutSessionRepository,
            )
            viewModel.load()
            val effectDeferred = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }
            viewModel.onEvent(WorkoutSessionEvent.OpenExerciseClicked("ex-barbell-squat"))
            val effect = effectDeferred.await()
            assertIs<WorkoutSessionEffect.OpenExercise>(effect)
            assertEquals("ex-barbell-squat", effect.exerciseId)
            assertEquals("preview", effect.runDraftId)
            assertEquals(null, WorkoutRunStore.activeForWorkout("preview"))
        }
    }

    @Test
    fun launchReadiness_ctaKeys_coverRequiredStates() {
        assertEquals("session_cta_preparing", LaunchReadiness.Preparing.ctaLabelKey())
        assertEquals("session_start", LaunchReadiness.Ready.ctaLabelKey())
        assertEquals("session_cta_offline_ready", LaunchReadiness.OfflineReady.ctaLabelKey())
        assertEquals("common_retry", LaunchReadiness.Blocked("x").ctaLabelKey())
        assertEquals("session_cta_launching", LaunchReadiness.Launching.ctaLabelKey())
        assertTrue(LaunchReadiness.Ready.canStart())
        assertTrue(!LaunchReadiness.Preparing.canStart())
    }

    @Test
    fun offlineReady_requiresAllConfigs_notPartialCache() {
        assertIs<LaunchReadiness.OfflineReady>(resolveOfflineConfigReadiness(allConfigsAvailable = true))
        val blocked = resolveOfflineConfigReadiness(allConfigsAvailable = false)
        assertIs<LaunchReadiness.Blocked>(blocked)
        assertEquals("training_config_offline_unavailable", blocked.reasonKey)
    }

    @Test
    fun resumeSnapshot_prefersDurableOverFreshSessionPlan() {
        val durable = WorkoutRunSnapshot(
            workoutId = "w1",
            title = "Saved",
            blocks = listOf(
                WorkoutRunBlock.Exercise(
                    exerciseId = "e-old",
                    slug = "squat",
                    displayName = "Squat",
                    phaseRole = "MAIN",
                    target = ExerciseTarget.Reps(8),
                    sets = 3,
                    restBetweenSetsMs = 0L,
                    restAfterExerciseMs = 0L,
                    poseVariantIndex = 0,
                    weightPerSetKg = null,
                ),
            ),
        )
        val fresh = WorkoutRunSnapshot(
            workoutId = "w1",
            title = "Fresh",
            blocks = listOf(
                WorkoutRunBlock.Exercise(
                    exerciseId = "e-new",
                    slug = "deadlift",
                    displayName = "Deadlift",
                    phaseRole = "MAIN",
                    target = ExerciseTarget.Reps(5),
                    sets = 3,
                    restBetweenSetsMs = 0L,
                    restAfterExerciseMs = 0L,
                    poseVariantIndex = 0,
                    weightPerSetKg = null,
                ),
            ),
        )
        val resolved = resolveResumeSnapshot(durableSnapshot = durable, sessionSnapshot = fresh)
        assertEquals("squat", resolved?.exercises?.single()?.slug)
        assertTrue(resumeProgressMatchesSnapshot(
            WorkoutRunProgressCursor(exerciseIndex = 0, exerciseSlug = "squat"),
            durable,
        ))
        assertTrue(!resumeProgressMatchesSnapshot(
            WorkoutRunProgressCursor(exerciseIndex = 0, exerciseSlug = "squat"),
            fresh,
        ))
    }
}
