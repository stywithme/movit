package com.movit.feature.library

import com.movit.shared.AppResult

interface ProgramFlowRepository {
    suspend fun loadPrograms(): AppResult<List<ProgramListItemUi>>
    suspend fun loadWeekPlan(programId: String, weekNumber: Int): AppResult<ProgramWeekPlanUi>
    suspend fun loadWeeklyReport(programId: String, weekNumber: Int): AppResult<WeeklyReportUi>
}

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
        return AppResult.Success(ProgramFlowPreviewData.weeklyReport(programId, weekNumber))
    }
}

fun defaultProgramFlowRepository(): ProgramFlowRepository = SharedProgramFlowRepository()
