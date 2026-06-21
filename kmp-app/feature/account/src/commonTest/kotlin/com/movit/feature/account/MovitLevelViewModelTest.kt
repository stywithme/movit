package com.movit.feature.account

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovitLevelViewModelTest {

    @Test
    fun load_success_populatesProfile() = runBlocking {
        val viewModel = MovitLevelViewModel(repository = FakeLevelRepository())
        viewModel.load()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertNotNull(state.profile)
        assertEquals(2, state.profile?.levelNumber)
        assertEquals(65, state.profile?.bodyScore)
    }

    @Test
    fun load_failure_showsError() = runBlocking {
        val viewModel = MovitLevelViewModel(
            repository = FakeLevelRepository(shouldFail = true),
        )
        viewModel.load()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
        assertNull(state.profile)
    }

    @Test
    fun tabSelected_switchesPlanTab() = runBlocking {
        val viewModel = MovitLevelViewModel(repository = FakeLevelRepository())
        viewModel.load()
        viewModel.onEvent(MovitLevelEvent.TabSelected(LevelTab.PlanOverview))

        assertEquals(LevelTab.PlanOverview, viewModel.state.value.selectedTab)
    }

    @Test
    fun load_levelUp_showsCelebrationWhenLevelIncreased() = runBlocking {
        var lastSeen = 1
        val preferences = LevelCelebrationPreferences(
            readLastSeenLevel = { lastSeen },
            writeLastSeenLevel = { lastSeen = it },
        )
        val viewModel = MovitLevelViewModel(
            repository = FakeLevelRepository(),
            celebrationPreferences = preferences,
        )
        viewModel.load()

        val celebration = viewModel.state.value.levelUpCelebration
        assertNotNull(celebration)
        assertEquals(1, celebration.fromLevel)
        assertEquals(2, celebration.toLevel)
        assertEquals("Building", celebration.levelName)
        assertEquals(2, lastSeen)
    }

    @Test
    fun dismissLevelUp_clearsCelebration() = runBlocking {
        val preferences = LevelCelebrationPreferences(
            readLastSeenLevel = { 1 },
            writeLastSeenLevel = {},
        )
        val viewModel = MovitLevelViewModel(
            repository = FakeLevelRepository(),
            celebrationPreferences = preferences,
        )
        viewModel.load()
        viewModel.onEvent(MovitLevelEvent.DismissLevelUpCelebration)

        assertNull(viewModel.state.value.levelUpCelebration)
    }

    @Test
    fun load_recoversAfterTransientFailure() = runBlocking {
        var calls = 0
        val repository = object : LevelRepository {
            override suspend fun fetchLevelProfile(): com.movit.shared.AppResult<LevelProfileUi> {
                calls++
                return if (calls == 1) {
                    com.movit.shared.AppResult.Failure("offline")
                } else {
                    com.movit.shared.AppResult.Success(FakeLevelPreviewData.profile)
                }
            }
        }
        val viewModel = MovitLevelViewModel(repository = repository)
        viewModel.load()
        assertTrue(viewModel.state.value.errorMessage != null)

        viewModel.load()
        assertNull(viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.profile != null)
    }
}
