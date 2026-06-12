package com.movit.feature.library

import com.movit.core.data.MovitData
import com.movit.core.data.cache.CacheState
import com.movit.core.data.cache.staleWhileRevalidate
import com.movit.resources.strings.ProgramFlowStrings
import com.movit.shared.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class SharedProgramFlowRepository : ProgramFlowRepository {

    override fun observePrograms(): Flow<CacheState<List<ProgramListItemUi>>> {
        if (!MovitData.isInstalled) {
            return flowOf(CacheState.Error(DATA_LAYER_NOT_INSTALLED))
        }

        val repo = MovitData.programFlow

        return staleWhileRevalidate(
            screenId = "program_list",
            readCached = {
                val platform = MovitData.requirePlatform()
                val language = platform.preferredLanguage()
                val strings = ProgramFlowStrings.load(language)
                val explore = repo.readCachedExplore() ?: return@staleWhileRevalidate null
                val home = repo.readCachedHome()
                val programs = ProgramFlowApiMapper.mapPrograms(explore, home, language, strings)
                programs.ifEmpty { null }
            },
            syncFresh = {
                val platform = MovitData.requirePlatform()
                val language = platform.preferredLanguage()
                val strings = ProgramFlowStrings.load(language)

                val explore = when (val result = repo.syncExplore()) {
                    is AppResult.Success -> result.value
                    is AppResult.Failure -> repo.readCachedExplore()
                } ?: return@staleWhileRevalidate AppResult.Failure(strings.programNotFound)

                val home = when (val result = repo.syncHome()) {
                    is AppResult.Success -> result.value
                    is AppResult.Failure -> repo.readCachedHome()
                }

                repo.refreshActiveUserProgramId()

                val programs = ProgramFlowApiMapper.mapPrograms(explore, home, language, strings)
                if (programs.isNotEmpty()) {
                    AppResult.Success(programs)
                } else {
                    AppResult.Failure(strings.programNotFound)
                }
            },
        )
    }

    override fun observeWeekPlan(
        programId: String,
        weekNumber: Int,
    ): Flow<CacheState<ProgramWeekPlanUi>> {
        if (!MovitData.isInstalled) {
            return flowOf(CacheState.Error(DATA_LAYER_NOT_INSTALLED))
        }

        val repo = MovitData.programFlow

        return staleWhileRevalidate(
            screenId = "program_week_plan",
            readCached = {
                val platform = MovitData.requirePlatform()
                val language = platform.preferredLanguage()
                val strings = ProgramFlowStrings.load(language)
                val program = repo.readCachedProgram(programId) ?: return@staleWhileRevalidate null
                val home = repo.readCachedHome()
                ProgramFlowApiMapper.mapWeekPlan(program, weekNumber, home, language, strings)
            },
            syncFresh = {
                val platform = MovitData.requirePlatform()
                val language = platform.preferredLanguage()
                val strings = ProgramFlowStrings.load(language)
                val program = when (val result = repo.syncProgram(programId)) {
                    is AppResult.Success -> result.value
                    is AppResult.Failure -> repo.readCachedProgram(programId)
                        ?: return@staleWhileRevalidate AppResult.Failure(result.message)
                }
                val home = loadHome(repo)
                val plan = ProgramFlowApiMapper.mapWeekPlan(program, weekNumber, home, language, strings)
                    ?: return@staleWhileRevalidate AppResult.Failure(strings.programNotFound)
                AppResult.Success(plan)
            },
        )
    }

    override suspend fun loadPrograms(): AppResult<List<ProgramListItemUi>> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
        }

        val platform = MovitData.requirePlatform()
        val language = platform.preferredLanguage()
        val strings = ProgramFlowStrings.load(language)
        val repo = MovitData.programFlow

        val explore = when (val result = repo.syncExplore()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> repo.readCachedExplore()
        } ?: return AppResult.Failure(strings.programNotFound)

        val home = when (val result = repo.syncHome()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> repo.readCachedHome()
        }

        repo.refreshActiveUserProgramId()

        val programs = ProgramFlowApiMapper.mapPrograms(explore, home, language, strings)
        return if (programs.isNotEmpty()) {
            AppResult.Success(programs)
        } else {
            AppResult.Failure(strings.programNotFound)
        }
    }

    override suspend fun loadWeekPlan(
        programId: String,
        weekNumber: Int,
    ): AppResult<ProgramWeekPlanUi> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
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
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
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

        val summaries = ProgramFlowApiMapper.mapWeekSummaries(
            program = program,
            metrics = metrics,
            language = language,
            strings = strings,
        )
        return AppResult.Success(
            ProgramFlowApiMapper.mapWeeklyReport(
                program = program,
                weekNumber = weekNumber,
                metrics = metrics,
                dashboardSummary = dashboardSummary,
                language = language,
                strings = strings,
            ).copy(weekSummaries = summaries),
        )
    }

    override suspend fun loadWeekReportSummaries(programId: String): AppResult<List<WeeklyReportWeekSummaryUi>> {
        if (!MovitData.isInstalled) {
            return AppResult.Failure(DATA_LAYER_NOT_INSTALLED)
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

        return AppResult.Success(
            ProgramFlowApiMapper.mapWeekSummaries(
                program = program,
                metrics = metrics,
                language = language,
                strings = strings,
            ),
        )
    }

    private suspend fun loadHome(repo: com.movit.core.data.repository.ProgramFlowSyncRepository): com.movit.core.network.dto.HomeDataDto? =
        when (val result = repo.syncHome()) {
            is AppResult.Success -> result.value
            is AppResult.Failure -> repo.readCachedHome()
        }

    private companion object {
        const val DATA_LAYER_NOT_INSTALLED = "App data layer is not installed."
    }
}
