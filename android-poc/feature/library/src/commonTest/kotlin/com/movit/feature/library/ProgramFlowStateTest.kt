package com.movit.feature.library

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProgramFlowStateTest {

    @Test
    fun programList_loadsPrograms() {
        runBlocking {
            val viewModel = ProgramListViewModel()
            viewModel.load()
            val state = viewModel.state.value
            assertEquals(false, state.isLoading)
            assertTrue(state.filteredPrograms.isNotEmpty())
            assertTrue(state.chips.contains("All"))
        }
    }

    @Test
    fun programList_chipFiltersByLevel() {
        runBlocking {
            val viewModel = ProgramListViewModel()
            viewModel.load()
            val beginnerCount = viewModel.state.value.programs.count {
                it.levelLabel.equals("Beginner", ignoreCase = true)
            }
            viewModel.onChipSelected("Beginner")
            assertEquals(beginnerCount, viewModel.state.value.filteredPrograms.size)
        }
    }

    @Test
    fun weekPlan_loadsDays() {
        runBlocking {
            val viewModel = ProgramWeekPlanViewModel("prog-full-body", weekNumber = 2)
            viewModel.load()
            val plan = viewModel.state.value.weekPlan
            assertNotNull(plan)
            assertEquals(2, plan.weekNumber)
            assertTrue(plan.days.isNotEmpty())
            assertNotNull(plan.todayDayNumber)
        }
    }

    @Test
    fun weeklyReport_loadsMetrics() {
        runBlocking {
            val viewModel = WeeklyReportViewModel("prog-full-body", weekNumber = 2)
            viewModel.load()
            val report = viewModel.state.value.report
            assertNotNull(report)
            assertEquals(4, report.sessionsCompleted)
            assertEquals(5, report.dailyScores.size)
        }
    }

    @Test
    fun repository_failure_setsError() {
        runBlocking {
            val viewModel = ProgramListViewModel(
                repository = FakeProgramFlowRepository(shouldFail = true),
            )
            viewModel.load()
            assertEquals("Unable to load programs.", viewModel.state.value.errorMessage)
        }
    }
}
