package com.movit.debug

import android.content.Context
import com.movit.feature.home.HomeDashboardUi
import com.movit.feature.home.HomeProgressUi
import com.movit.feature.home.HomeQuickActionUi
import com.movit.feature.home.HomeReportPreviewUi
import com.movit.feature.home.HomeTrainingPlanUi
import com.movit.feature.home.remote.HomeContentFetcher
import com.movit.feature.home.remote.HomeContentFetcherBridge
import com.movit.shared.AppResult
import com.trainingvalidator.poc.network.HomeData
import com.trainingvalidator.poc.storage.HomeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Wires Movit Home KMP to the existing Retrofit-backed home cache.
 * Installed once from debug pilot hosts before Compose content is set.
 */
object MovitHomeApiBridge {

    fun install(context: Context) {
        val appContext = context.applicationContext
        HomeContentFetcherBridge.fetcher = HomeContentFetcher {
            fetchFromLegacyRepository(appContext)
        }
    }

    private suspend fun fetchFromLegacyRepository(context: Context): AppResult<HomeDashboardUi> {
        return withContext(Dispatchers.IO) {
            try {
                val repository = HomeRepository.getInstance(context)
                val data = repository.syncFromServer() ?: repository.getCachedData()
                if (data == null) {
                    AppResult.Failure("No home data available. Check your connection.")
                } else {
                    AppResult.Success(mapHomeData(data))
                }
            } catch (error: Exception) {
                AppResult.Failure(
                    message = error.message ?: "Home sync failed.",
                    cause = error,
                )
            }
        }
    }
}

internal fun mapHomeData(data: HomeData): HomeDashboardUi {
    val userName = data.user?.name?.takeIf { it.isNotBlank() }
    val trainMode = data.trainMode
    val program = trainMode?.activeProgram
    val workout = trainMode?.todayWorkout

    val todayPlan = workout?.let {
        HomeTrainingPlanUi(
            title = it.name.localized().ifBlank { "Today's workout" },
            subtitle = program?.let { p ->
                "${p.name.localized()} · Week ${p.weekNumber} · Day ${p.dayNumber}"
            } ?: "Today's session",
            durationLabel = it.estimatedMinutes?.let { min -> "~$min min" } ?: "—",
            exerciseCountLabel = "${it.exerciseCount} exercises",
            statusLabel = when {
                it.isCompleted -> "Completed today"
                it.allWorkoutsCount > 1 ->
                    "Workout ${it.completedWorkoutsCount + 1} of ${it.allWorkoutsCount} today"
                else -> "Ready to start"
            },
        )
    }

    val weekProgress = program?.weekProgress
    val weeklyPercent = if (weekProgress != null && weekProgress.total > 0) {
        (weekProgress.completed * 100) / weekProgress.total
    } else {
        0
    }

    val stats = data.stats
    val progress = HomeProgressUi(
        weeklyCompletionPercent = weeklyPercent,
        streakDays = stats?.streak ?: 0,
        activeMinutesLabel = "${stats?.totalMinutes ?: 0} min",
        formScoreLabel = stats?.avgFormScore?.let { "$it%" } ?: "—",
    )

    val reportPreview = data.recentWorkouts?.firstOrNull()?.let { recent ->
        HomeReportPreviewUi(
            title = "Last session",
            subtitle = recent.exerciseName.localized(),
            scoreLabel = "${recent.formScore}%",
            trendLabel = "${recent.totalReps} reps",
        )
    }

    val insightMessage = data.alerts?.firstOrNull()?.messageEn?.takeIf { it.isNotBlank() }

    return HomeDashboardUi(
        greetingTitle = userName?.let { "Hi, $it" } ?: "Welcome back",
        greetingSubtitle = greetingSubtitleFor(trainMode?.status),
        todayPlan = todayPlan,
        progress = progress,
        reportPreview = reportPreview,
        quickActions = defaultQuickActions,
        insightMessage = insightMessage,
    )
}

private fun greetingSubtitleFor(status: String?): String = when (status) {
    "active" -> "Ready for today's session?"
    "rest_day" -> "Rest day — recover and come back stronger."
    "no_plan" -> "Let's find your next workout."
    "no_assessment" -> "Start with a body scan to get your plan."
    "program_complete" -> "Program complete — time to reassess."
    "reassessment_due" -> "Reassessment due — let's recalibrate."
    else -> "Let's get moving."
}

private val defaultQuickActions = listOf(
    HomeQuickActionUi("train", "Train", "Start or resume training"),
    HomeQuickActionUi("explore", "Explore", "Browse workouts and exercises"),
    HomeQuickActionUi("reports", "Reports", "Review your progress"),
    HomeQuickActionUi("profile", "Profile", "Account and preferences"),
)

private fun Map<String, String>.localized(): String =
    this["en"]?.takeIf { it.isNotBlank() }
        ?: this["ar"]?.takeIf { it.isNotBlank() }
        ?: values.firstOrNull().orEmpty()
