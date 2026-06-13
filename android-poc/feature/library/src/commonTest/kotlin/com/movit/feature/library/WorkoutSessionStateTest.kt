package com.movit.feature.library

import com.movit.shared.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkoutSessionStateTest {

    @Test
    fun sessionKey_roundTrips() {
        val encoded = WorkoutSessionKeys.encode(
            programId = "prog-123",
            weekNumber = 2,
            dayNumber = 3,
            plannedWorkoutId = "workout-abc",
        )
        val parsed = WorkoutSessionKeys.parse(encoded)
        assertNotNull(parsed)
        assertEquals("prog-123", parsed.programId)
        assertEquals(2, parsed.weekNumber)
        assertEquals(3, parsed.dayNumber)
        assertEquals("workout-abc", parsed.plannedWorkoutId)
    }

    @Test
    fun firstExerciseId_returnsWarmupExercise() {
        val session = WorkoutSessionPreviewData.preview
        assertEquals("ex-squat-warm", session.firstExerciseId())
        assertEquals("ex-barbell-squat", session.withoutWarmup().firstExerciseId())
    }

    @Test
    fun preview_loadsSections() {
        val viewModel = WorkoutSessionViewModel(
            workoutId = "preview",
            repository = PreviewWorkoutSessionRepository,
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        val session = viewModel.state.value.session
        assertNotNull(session)
        assertTrue(session.sections.isNotEmpty())
        assertEquals(6, session.exerciseCount)
    }

    @Test
    fun editDetails_updatesExerciseMetrics() {
        val viewModel = WorkoutSessionViewModel(
            workoutId = "preview",
            repository = PreviewWorkoutSessionRepository,
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        viewModel.toggleEditMode()
        viewModel.openEditSheet("ex-barbell-squat")
        viewModel.updateEditDraft { it.copy(sets = 4, reps = 8, weightKg = 50f, restSeconds = 90) }
        viewModel.saveEditDetails()

        val exercise = viewModel.state.value.session
            ?.sections
            ?.flatMap { it.items }
            ?.filterIsInstance<WorkoutSessionBlockUi.Exercise>()
            ?.first { it.id == "ex-barbell-squat" }

        assertNotNull(exercise)
        assertEquals(4, exercise.sets)
        assertEquals(8, exercise.reps)
        assertEquals(50f, exercise.weightKg)
        assertEquals("4 × 8", exercise.setsLabel)
        assertEquals("50 kg", exercise.weightLabel)
        assertEquals("90s rest", exercise.restLabel)
    }

    @Test
    fun deleteExercise_removesBlockAndRecalculates() {
        val viewModel = WorkoutSessionViewModel(
            workoutId = "preview",
            repository = PreviewWorkoutSessionRepository,
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        val before = viewModel.state.value.session?.exerciseCount ?: 0
        viewModel.toggleEditMode()
        viewModel.deleteExercise("ex-barbell-squat")
        val after = viewModel.state.value.session?.exerciseCount ?: 0
        assertEquals(before - 1, after)
    }

    @Test
    fun moveBlock_reordersWithinSection() {
        val viewModel = WorkoutSessionViewModel(
            workoutId = "preview",
            repository = PreviewWorkoutSessionRepository,
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        viewModel.toggleEditMode()
        val mainSection = viewModel.state.value.session?.sections
            ?.first { it.phaseRole == "MAIN" }
            ?: return
        val firstId = mainSection.items.first().id
        val secondId = mainSection.items[1].id
        viewModel.moveBlock("MAIN", firstId, 1)
        val reordered = viewModel.state.value.session?.sections
            ?.first { it.phaseRole == "MAIN" }
            ?.items
            ?.map { it.id }
        assertEquals(listOf(secondId, firstId), reordered?.take(2))
    }

    @Test
    fun skipWarmup_setsFlagWithoutRemovingPersistedSections() {
        val viewModel = WorkoutSessionViewModel(
            workoutId = "preview",
            repository = PreviewWorkoutSessionRepository,
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        viewModel.skipWarmup()
        val session = viewModel.state.value.session
        assertNotNull(session)
        assertTrue(session.warmupSkipped)
        assertTrue(session.sections.any { it.phaseRole == "WARMUP" })
        assertEquals("ex-barbell-squat", session.firstExerciseId())
    }

    @Test
    fun addRestBlock_appendsRestToMainSection() {
        val viewModel = WorkoutSessionViewModel(
            workoutId = "preview",
            repository = PreviewWorkoutSessionRepository,
        )
        kotlinx.coroutines.runBlocking { viewModel.load() }
        viewModel.toggleEditMode()
        val before = viewModel.state.value.session?.sections
            ?.first { it.phaseRole == "MAIN" }
            ?.items
            ?.count { it is WorkoutSessionBlockUi.Rest }
            ?: 0
        viewModel.addRestBlock()
        val after = viewModel.state.value.session?.sections
            ?.first { it.phaseRole == "MAIN" }
            ?.items
            ?.count { it is WorkoutSessionBlockUi.Rest }
            ?: 0
        assertEquals(before + 1, after)
    }
}

private object PreviewWorkoutSessionRepository : WorkoutSessionRepository {
    override suspend fun loadSession(workoutId: String): AppResult<WorkoutSessionUi> =
        AppResult.Success(WorkoutSessionPreviewData.preview)

    override suspend fun loadDayContext(workoutId: String): SessionDayContext = SessionDayContext()

    override suspend fun saveSession(session: WorkoutSessionUi): AppResult<Unit> = AppResult.Success(Unit)

    override suspend fun saveFlowCustomization(workoutId: String, config: WorkoutFlowConfigUi): AppResult<Unit> =
        AppResult.Success(Unit)

    override suspend fun sessionKeyForDay(programId: String, weekNumber: Int, dayNumber: Int): String? = null

    override suspend fun findSwapCandidates(query: String, replacingSlug: String): List<SessionSwapCandidateUi> =
        emptyList()

    override suspend fun findAddExerciseCandidates(query: String): List<SessionSwapCandidateUi> = emptyList()
}
