package com.movit.feature.library

import com.movit.feature.explore.ExploreContent
import com.movit.feature.explore.ExploreItemType
import com.movit.feature.explore.ExploreItemUi
import com.movit.shared.AppResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun load_mapsWeeksAndStats() = kotlinx.coroutines.runBlocking {
        val viewModel = ProgramDetailViewModel(
            programId = "program-starter",
            repository = FakeProgramLibraryRepository(sampleProgram),
        )
        viewModel.load()

        val state = viewModel.state.value
        assertEquals("Starter Strength Plan", state.title)
        assertEquals(4, state.weeks.size)
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

        assertEquals(2, viewModel.state.value.selectedWeekNumber)
    }

    @Test
    fun startProgram_enrollsAndReturnsSessionKey() = kotlinx.coroutines.runBlocking {
        val viewModel = ProgramDetailViewModel(
            programId = "program-starter",
            repository = FakeProgramLibraryRepository(sampleProgram),
        )
        viewModel.load()

        val key = viewModel.sessionKeyForStart()
        assertNotNull(key)
        assertTrue(key.startsWith("session:"))
        assertTrue(viewModel.state.value.enrollment.isEnrolled)
    }

    @Test
    fun editSave_incrementsCustomEdits() = kotlinx.coroutines.runBlocking {
        val viewModel = ProgramDetailViewModel(
            programId = "program-starter",
            repository = FakeProgramLibraryRepository(sampleProgram),
        )
        viewModel.load()
        viewModel.onSaveEdit()

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
