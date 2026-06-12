package com.movit.feature.library

import com.movit.shared.AppResult

class FakeProgramFlowRepository(
    private val programs: List<ProgramListItemUi> = ProgramFlowPreviewData.programs,
    private val shouldFail: Boolean = false,
) : ProgramFlowRepository {

    override suspend fun loadPrograms(): AppResult<List<ProgramListItemUi>> {
        if (shouldFail) return AppResult.Failure("Unable to load programs.")
        return AppResult.Success(programs)
    }

    override suspend fun loadWeekPlan(programId: String, weekNumber: Int): AppResult<ProgramWeekPlanUi> {
        if (shouldFail) return AppResult.Failure("Unable to load week plan.")
        val exists = programs.any { it.id == programId }
        if (!exists) return AppResult.Failure("Program not found.")
        return AppResult.Success(ProgramFlowPreviewData.weekPlan(programId, weekNumber))
    }

    override suspend fun loadWeeklyReport(programId: String, weekNumber: Int): AppResult<WeeklyReportUi> {
        if (shouldFail) return AppResult.Failure("Unable to load weekly report.")
        val exists = programs.any { it.id == programId }
        if (!exists) return AppResult.Failure("Program not found.")
        val summaries = loadWeekReportSummaries(programId).let { result ->
            when (result) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> emptyList()
            }
        }
        return AppResult.Success(
            ProgramFlowPreviewData.weeklyReport(programId, weekNumber).copy(weekSummaries = summaries),
        )
    }

    override suspend fun loadWeekReportSummaries(programId: String): AppResult<List<WeeklyReportWeekSummaryUi>> {
        if (shouldFail) return AppResult.Failure("Unable to load weekly report.")
        val program = programs.firstOrNull { it.id == programId } ?: return AppResult.Failure("Program not found.")
        val summaries = (1..program.durationWeeks).map { weekNumber ->
            val report = ProgramFlowPreviewData.weeklyReport(programId, weekNumber)
            WeeklyReportWeekSummaryUi(
                weekNumber = weekNumber,
                title = "Week $weekNumber",
                progressPercent = if (weekNumber == 2) 80 else 40,
                sessionsCompleted = report.sessionsCompleted,
                sessionsPlanned = report.sessionsPlanned,
                avgFormPercent = report.avgFormPercent,
                totalReps = report.totalReps,
                message = report.heroSubtitle,
            )
        }
        return AppResult.Success(summaries)
    }
}
