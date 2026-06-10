package com.movit.feature.library

import com.movit.core.data.MovitData
import com.movit.core.network.dto.ExploreDataDto
import com.movit.core.network.dto.HomeDataDto
import com.movit.resources.strings.ProgramFlowStrings
import com.movit.shared.AppResult

class SharedProgramFlowRepository(
    private val fallback: ProgramFlowRepository = FakeProgramFlowRepository(),
) : ProgramFlowRepository {

    override suspend fun loadPrograms(): AppResult<List<ProgramListItemUi>> {
        if (!MovitData.isInstalled) {
            return fallback.loadPrograms()
        }

        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = ProgramFlowStrings.load(language)
        val repo = MovitData.programFlow

        val explore = when (val result = repo.syncExplore()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> repo.readCachedExplore()
        } ?: return fallback.loadPrograms()

        val home = when (val result = repo.syncHome()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> repo.readCachedHome()
        }

        repo.refreshActiveUserProgramId()

        val programs = ProgramFlowApiMapper.mapPrograms(explore, home, language, strings)
        return if (programs.isNotEmpty()) {
            AppResult.Success(programs)
        } else {
            fallback.loadPrograms()
        }
    }

    override suspend fun loadWeekPlan(
        programId: String,
        weekNumber: Int,
    ): AppResult<ProgramWeekPlanUi> {
        if (!MovitData.isInstalled) {
            return fallback.loadWeekPlan(programId, weekNumber)
        }

        if (isPreviewProgram(programId)) {
            return fallback.loadWeekPlan(programId, weekNumber)
        }

        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = ProgramFlowStrings.load(language)
        val repo = MovitData.programFlow

        val program = when (val result = repo.syncProgram(programId)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> repo.readCachedProgram(programId)
                ?: return AppResult.Failure(result.message)
        }

        val home = loadHome(repo)
        val plan = ProgramFlowApiMapper.mapWeekPlan(program, weekNumber, home, language, strings)
            ?: return AppResult.Failure(strings.programNotFound)

        return AppResult.Success(plan)
    }

    override suspend fun loadWeeklyReport(
        programId: String,
        weekNumber: Int,
    ): AppResult<WeeklyReportUi> {
        if (!MovitData.isInstalled) {
            return fallback.loadWeeklyReport(programId, weekNumber)
        }

        if (isPreviewProgram(programId)) {
            return fallback.loadWeeklyReport(programId, weekNumber)
        }

        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = ProgramFlowStrings.load(language)
        val repo = MovitData.programFlow

        val program = when (val result = repo.syncProgram(programId)) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> repo.readCachedProgram(programId)
                ?: return AppResult.Failure(result.message)
        }

        val metrics = platform.activeUserProgramId()?.let { userProgramId ->
            when (val result = repo.syncProgressMetrics(userProgramId)) {
                is AppResult.Success -> result.value
                is AppResult.Failure -> null
            }
        }

        val dashboardSummary = if (platform.isProUser()) {
            when (val result = MovitData.reports.syncDashboard(programId = programId, period = "week")) {
                is AppResult.Success -> result.value.summary
                is AppResult.Failure -> null
            }
        } else {
            null
        }

        return AppResult.Success(
            ProgramFlowApiMapper.mapWeeklyReport(
                program = program,
                weekNumber = weekNumber,
                metrics = metrics,
                dashboardSummary = dashboardSummary,
                language = language,
                strings = strings,
            ),
        )
    }

    private suspend fun loadHome(repo: com.movit.core.data.repository.ProgramFlowSyncRepository): HomeDataDto? =
        when (val result = repo.syncHome()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> repo.readCachedHome()
        }

    private fun isPreviewProgram(programId: String): Boolean =
        programId == "preview" || ProgramFlowPreviewData.programs.any { it.id == programId }
}
