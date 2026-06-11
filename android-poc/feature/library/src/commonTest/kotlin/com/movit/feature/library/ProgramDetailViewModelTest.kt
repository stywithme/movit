package com.movit.feature.library

import com.movit.core.model.ExploreContent
import com.movit.core.model.ExploreItemType
import com.movit.core.model.ExploreItemUi
import com.movit.shared.AppResult
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgramDetailViewModelTest {

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
        assertEquals("No upcoming session found for this program.", viewModel.state.value.errorMessage)
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
