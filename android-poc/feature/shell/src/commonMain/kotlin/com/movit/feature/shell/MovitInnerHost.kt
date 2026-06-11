package com.movit.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.feature.account.MovitAssessmentRoute
import com.movit.feature.account.MovitAuthRoute
import com.movit.feature.account.MovitLevelRoute
import com.movit.feature.account.MovitOnboardingRoute
import com.movit.feature.library.ExercisePrepareRoute
import com.movit.feature.library.ExercisesLibraryRoute
import com.movit.feature.library.ProgramDetailRoute
import com.movit.feature.library.ProgramListRoute
import com.movit.feature.library.ProgramWeekPlanRoute
import com.movit.feature.library.WeeklyReportEffect
import com.movit.feature.library.WeeklyReportRoute
import com.movit.feature.training.ExerciseLiveRoute
import com.movit.feature.training.TrainingSessionRoute
import com.movit.feature.library.TrainingStartAction
import com.movit.feature.library.WorkoutCustomizeRoute
import com.movit.feature.library.WorkoutRunRoute
import com.movit.feature.library.WorkoutSessionRoute
import com.movit.feature.library.WorkoutsLibraryRoute
import com.movit.feature.reports.ReportDetailEffect
import com.movit.feature.reports.ReportDetailRoute

@Composable
fun MovitInnerHost(
    route: MovitInnerRoute,
    onBack: () -> Unit,
    onNavigate: (MovitInnerRoute) -> Unit,
    onShellEvent: (MovitAppShellEvent) -> Unit = {},
    onShellEffect: (MovitAppShellEffect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    when (route) {
        MovitInnerRoute.ExercisesLibrary -> {
            ExercisesLibraryRoute(
                onBack = onBack,
                onItemClick = { onNavigate(MovitInnerRoute.ExercisePrepare(it)) },
                modifier = modifier,
            )
        }
        MovitInnerRoute.WorkoutsLibrary -> {
            WorkoutsLibraryRoute(
                onBack = onBack,
                onItemClick = { onNavigate(MovitInnerRoute.WorkoutSession(it)) },
                modifier = modifier,
            )
        }
        MovitInnerRoute.ProgramList -> {
            ProgramListRoute(
                onBack = onBack,
                onProgramClick = { programId ->
                    onNavigate(MovitInnerRoute.ProgramWeekPlan(programId = programId, weekNumber = 1))
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.ProgramWeekPlan -> {
            ProgramWeekPlanRoute(
                programId = route.programId,
                weekNumber = route.weekNumber,
                onBack = onBack,
                onOpenSession = { sessionKey ->
                    onNavigate(MovitInnerRoute.WorkoutSession(sessionKey))
                },
                onViewWeeklyReport = {
                    onNavigate(
                        MovitInnerRoute.WeeklyReport(
                            programId = route.programId,
                            weekNumber = route.weekNumber,
                        ),
                    )
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.WeeklyReport -> {
            WeeklyReportRoute(
                programId = route.programId,
                weekNumber = route.weekNumber,
                onBack = onBack,
                onEffect = { effect ->
                    if (effect == WeeklyReportEffect.ShareRequested) {
                        onShellEffect(MovitAppShellEffect.ShowLocalizedMessage("program_flow_share_coming_soon"))
                    }
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.ProgramDetail -> {
            ProgramDetailRoute(
                programId = route.programId,
                onBack = onBack,
                onStartSession = { sessionKey ->
                    onNavigate(MovitInnerRoute.WorkoutSession(sessionKey))
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.WorkoutSession -> {
            WorkoutSessionRoute(
                workoutId = route.workoutId,
                onBack = onBack,
                onExerciseClick = {
                    onNavigate(
                        MovitInnerRoute.ExercisePrepare(
                            exerciseId = it,
                            workoutId = route.workoutId,
                        ),
                    )
                },
                onStartWorkout = {
                    onNavigate(MovitInnerRoute.WorkoutCustomize(route.workoutId))
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.WorkoutCustomize -> {
            WorkoutCustomizeRoute(
                workoutId = route.workoutId,
                onBack = onBack,
                onStart = { onNavigate(MovitInnerRoute.WorkoutRun(route.workoutId)) },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.WorkoutRun -> {
            WorkoutRunRoute(
                workoutId = route.workoutId,
                onBack = onBack,
                onStartExercise = { action ->
                    handleTrainingStart(action, workoutId = route.workoutId, onNavigate, onShellEffect)
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.ExerciseLive -> {
            ExerciseLiveRoute(
                exerciseSlug = route.exerciseSlug,
                exerciseName = route.exerciseName,
                targetReps = route.targetReps,
                onBack = onBack,
                onFinish = {
                    val workoutId = route.workoutId
                    if (workoutId != null) {
                        onNavigate(MovitInnerRoute.WorkoutRun(workoutId))
                    } else {
                        onBack()
                    }
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.TrainingSession -> {
            TrainingSessionRoute(
                exerciseSlug = route.exerciseSlug,
                exerciseName = route.exerciseName,
                targetReps = route.targetReps,
                onBack = onBack,
                onFinish = {
                    val workoutId = route.workoutId
                    if (workoutId != null) {
                        onNavigate(MovitInnerRoute.WorkoutRun(workoutId))
                    } else {
                        onBack()
                    }
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.ExercisePrepare -> {
            ExercisePrepareRoute(
                exerciseId = route.exerciseId,
                onBack = onBack,
                onStart = { action ->
                    handleTrainingStart(
                        action = action,
                        workoutId = route.workoutId,
                        onNavigate = onNavigate,
                        onShellEffect = onShellEffect,
                    )
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.ReportDetail -> {
            ReportDetailRoute(
                reportId = route.reportId,
                onBack = onBack,
                onEffect = { effect ->
                    val key = when (effect) {
                        ReportDetailEffect.ShareRequested -> "shell_report_share_coming_soon"
                        ReportDetailEffect.ExportRequested -> "shell_report_export_coming_soon"
                    }
                    onShellEffect(MovitAppShellEffect.ShowLocalizedMessage(key))
                },
                modifier = modifier,
            )
        }
        MovitInnerRoute.Auth -> {
            MovitAuthRoute(
                onEffect = { onShellEvent(MovitAppShellEvent.AuthEffectReceived(it)) },
                modifier = modifier,
            )
        }
        MovitInnerRoute.ProfileOnboarding -> {
            MovitOnboardingRoute(
                onEffect = { onShellEvent(MovitAppShellEvent.OnboardingEffectReceived(it)) },
                modifier = modifier,
            )
        }
        MovitInnerRoute.Assessment -> {
            MovitAssessmentRoute(
                onEffect = { onShellEvent(MovitAppShellEvent.AssessmentEffectReceived(it)) },
                modifier = modifier,
            )
        }
        MovitInnerRoute.LevelProfile -> {
            MovitLevelRoute(
                onBack = onBack,
                onEffect = { onShellEvent(MovitAppShellEvent.LevelEffectReceived(it)) },
                modifier = modifier,
            )
        }
    }
}

private fun handleTrainingStart(
    action: TrainingStartAction?,
    workoutId: String? = null,
    onNavigate: (MovitInnerRoute) -> Unit,
    onShellEffect: (MovitAppShellEffect) -> Unit,
) {
    when (action) {
        is TrainingStartAction.KmpLive -> {
            onNavigate(
                MovitInnerRoute.TrainingSession(
                    exerciseSlug = action.slug,
                    exerciseName = action.exerciseName,
                    targetReps = action.targetReps,
                    workoutId = action.workoutId ?: workoutId,
                ),
            )
        }
        is TrainingStartAction.Legacy -> {
            onShellEffect(
                MovitAppShellEffect.ShowLocalizedMessage("prepare_training_bridge_unavailable"),
            )
        }
        null -> {
            onShellEffect(
                MovitAppShellEffect.ShowLocalizedMessage("prepare_training_bridge_unavailable"),
            )
        }
    }
}
