package com.movit.feature.library

import com.movit.core.model.ExploreContent
import com.movit.core.model.ExploreItemType
import com.movit.core.model.ExploreItemUi
import com.movit.core.data.sync.WeekOfflinePackPrefetcher
import com.movit.core.network.dto.EffectivePlanItemDto
import com.movit.core.network.dto.EffectivePlanPayloadDto
import com.movit.core.network.dto.EffectivePlannedWorkoutDto
import com.movit.core.network.dto.ProgramExportDto
import com.movit.core.network.dto.ProgramExportWeekDto
import com.movit.core.network.dto.UserProgramUpdateRequest
import com.movit.shared.AppResult
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgramDetailViewModelTest {

    private val sampleProgramExport = ProgramExportDto(
        id = "program-starter",
        slug = "program-starter",
        weeks = listOf(ProgramExportWeekDto(weekNumber = 1)),
    )

    private val sampleProgram = ExploreItemUi(
        id = "program-starter",
        title = "Starter Strength Plan",
        subtitle = "Four-week progression.",
        type = ExploreItemType.Program,
        metadata = listOf("4 weeks", "3 days/week"),
    )

    @Test
    fun load_mapsStatsWithoutFakeWeeks() = kotlinx.coroutines.runBlocking {
        val viewModel = ProgramDetailViewModel(
            programId = "program-starter",
            repository = FakeProgramLibraryRepository(sampleProgram),
        )
        viewModel.load()

        val state = viewModel.state.value
        assertEquals("Starter Strength Plan", state.title)
        assertEquals(0, state.weeks.size)
        assertEquals(4, state.stats.size)
        assertEquals("4 weeks", state.stats.first().value)
        assertTrue(state.edit.daySessions.isNotEmpty())
    }

    @Test
    fun weekSelection_updatesSelectedWeek() = kotlinx.coroutines.runBlocking {
        val viewModel = ProgramDetailViewModel(
            programId = "program-starter",
            repository = FakeProgramLibraryRepository(sampleProgram),
        )
        viewModel.load()
        viewModel.onWeekSelected(2)
        delay(50)

        assertEquals(2, viewModel.state.value.selectedWeekNumber)
        assertEquals(2, viewModel.state.value.edit.editingWeekNumber)
    }

    @Test
    fun startProgram_callsEnrollApi() = kotlinx.coroutines.runBlocking {
        var enrolledId: String? = null
        val viewModel = ProgramDetailViewModel(
            programId = "program-starter",
            repository = FakeProgramLibraryRepository(sampleProgram),
            enrollProgram = { programId ->
                enrolledId = programId
                AppResult.Success("up-1")
            },
        )
        viewModel.load()

        val key = viewModel.startProgramAndGetSessionKey()
        assertEquals("program-starter", enrolledId)
        assertTrue(viewModel.state.value.enrollment.isEnrolled)
        assertNull(key)
        assertEquals("program_no_upcoming_session", viewModel.state.value.errorMessage)
    }

    @Test
    fun startProgram_enrollFailure_setsError() = kotlinx.coroutines.runBlocking {
        val viewModel = ProgramDetailViewModel(
            programId = "program-starter",
            repository = FakeProgramLibraryRepository(sampleProgram),
            enrollProgram = { AppResult.Failure("Enrollment failed.") },
        )
        viewModel.load()

        val key = viewModel.startProgramAndGetSessionKey()
        assertNull(key)
        assertEquals("Enrollment failed.", viewModel.state.value.errorMessage)
    }

    @Test
    fun editSave_incrementsCustomEdits() = kotlinx.coroutines.runBlocking {
        val viewModel = ProgramDetailViewModel(
            programId = "program-starter",
            repository = FakeProgramLibraryRepository(sampleProgram),
        )
        viewModel.load()
        viewModel.onSaveEdit()
        delay(50)

        assertEquals(1, viewModel.state.value.enrollment.customEditsCount)
        assertTrue(viewModel.state.value.edit.showSaveToast)
    }

    @Test
    fun sessionReorder_marksDirtyAndUpdatesOrder() = kotlinx.coroutines.runBlocking {
        val viewModel = ProgramDetailViewModel(
            programId = "program-starter",
            repository = FakeProgramLibraryRepository(sampleProgram),
        )
        viewModel.load()
        val firstId = viewModel.state.value.edit.daySessions.first().id
        viewModel.onSessionMove(firstId, direction = 1)
        delay(50)

        assertTrue(viewModel.state.value.edit.isDirty)
        val sessions = viewModel.state.value.edit.daySessions
        assertEquals("session-pm", sessions.first().id)
    }

    @Test
    fun editSave_callsSaveApiWhenDirty() = kotlinx.coroutines.runBlocking {
        var savedRequest: UserProgramUpdateRequest? = null
        val viewModel = ProgramDetailViewModel(
            programId = "program-starter",
            repository = FakeProgramLibraryRepository(sampleProgram),
            saveDayCustomizations = { _, week, day, request ->
                savedRequest = request
                assertEquals(1, week)
                assertEquals(2, day)
                AppResult.Success(Unit)
            },
        )
        viewModel.load()
        val session = viewModel.state.value.edit.daySessions.first()
        val exercise = session.exercises.first()
        viewModel.onExerciseParamChange(
            sessionId = session.id,
            exerciseId = exercise.id,
            sets = exercise.sets + 1,
        )
        viewModel.onSaveEdit()
        delay(100)

        assertTrue(savedRequest != null)
        assertEquals(1, viewModel.state.value.enrollment.customEditsCount)
        assertEquals(false, viewModel.state.value.edit.isDirty)
    }

    @Test
    fun editSaveEncoder_updatesExerciseParams() {
        val baseline = EffectivePlanPayloadDto(
            plannedWorkouts = listOf(
                EffectivePlannedWorkoutDto(
                    id = "pw-1",
                    sortOrder = 0,
                    items = listOf(
                        EffectivePlanItemDto(
                            id = "ex-1",
                            type = "exercise",
                            sets = 3,
                            targetReps = 12,
                            restBetweenSetsMs = 60_000,
                        ),
                    ),
                ),
            ),
        )
        val sessions = listOf(
            ProgramEditSessionUi(
                id = "pw-1",
                title = "Main",
                sortOrder = 0,
                exercises = listOf(
                    ProgramEditExerciseUi(
                        id = "ex-1",
                        name = "Squat",
                        sets = 4,
                        reps = 10,
                        weightKg = 20.0,
                        restSeconds = 90,
                    ),
                ),
            ),
        )
        val request = ProgramEditSaveEncoder.encodeDayUpdate(
            weekNumber = 1,
            dayNumber = 2,
            sessions = sessions,
            baselinePlan = baseline,
        )
        val item = request.customizations["day_1_2"]!!.single().items.single()
        assertEquals(4, item.sets)
        assertEquals(10, item.targetReps)
        assertEquals(90_000, item.restBetweenSetsMs)
    }

    @Test
    fun downloadWeekOffline_success_marksReady() = kotlinx.coroutines.runBlocking {
        val viewModel = ProgramDetailViewModel(
            programId = "program-starter",
            repository = FakeProgramLibraryRepository(sampleProgram),
            enrollProgram = { AppResult.Success("up-1") },
            programExportLoader = { sampleProgramExport },
            prefetchWeekOffline = { _, _, onProgress ->
                onProgress(50)
                WeekOfflinePackPrefetcher.PrefetchOutcome.Ready(
                    WeekOfflinePackPrefetcher.WeekPrefetchPlan(
                        programId = "program-starter",
                        weekNumber = 1,
                        plannedWorkoutIds = listOf("pw-1"),
                        workoutDayNumbers = listOf(1),
                        coverImageUrl = null,
                        exerciseSlugs = listOf("squat"),
                        imageUrls = emptyList(),
                        configsCached = 1,
                    ),
                )
            },
            isWeekOfflineReady = { _, _ -> false },
        )
        viewModel.load()
        viewModel.startProgramAndGetSessionKey()
        viewModel.onDownloadWeekOffline()
        delay(100)

        assertEquals(WeekOfflineStatus.Ready, viewModel.state.value.weekOffline.status)
        assertEquals(100, viewModel.state.value.weekOffline.progressPercent)
    }

    @Test
    fun downloadWeekOffline_failure_showsMessage() = kotlinx.coroutines.runBlocking {
        val viewModel = ProgramDetailViewModel(
            programId = "program-starter",
            repository = FakeProgramLibraryRepository(sampleProgram),
            enrollProgram = { AppResult.Success("up-1") },
            programExportLoader = { sampleProgramExport },
            prefetchWeekOffline = { _, _, _ ->
                WeekOfflinePackPrefetcher.PrefetchOutcome.Failed("Network unavailable.")
            },
            isWeekOfflineReady = { _, _ -> false },
        )
        viewModel.load()
        viewModel.startProgramAndGetSessionKey()
        viewModel.onDownloadWeekOffline()
        delay(100)

        assertEquals(WeekOfflineStatus.Failed, viewModel.state.value.weekOffline.status)
        assertEquals("Network unavailable.", viewModel.state.value.weekOffline.errorMessage)
    }

    @Test
    fun publish_reflectsPersistedOfflineReady() = kotlinx.coroutines.runBlocking {
        val viewModel = ProgramDetailViewModel(
            programId = "program-starter",
            repository = FakeProgramLibraryRepository(sampleProgram),
            isWeekOfflineReady = { programId, week -> programId == "program-starter" && week == 1 },
        )
        viewModel.load()
        delay(50)

        assertEquals(WeekOfflineStatus.Ready, viewModel.state.value.weekOffline.status)
    }
}

private class FakeProgramLibraryRepository(
    private val program: ExploreItemUi,
) : LibraryRepository {
    override suspend fun loadContent(): AppResult<ExploreContent> =
        AppResult.Success(
            ExploreContent(
                featured = emptyList(),
                workouts = emptyList(),
                exercises = emptyList(),
                programs = listOf(program),
            ),
        )

    override suspend fun findItem(id: String): ExploreItemUi? =
        program.takeIf { it.id == id }
}
