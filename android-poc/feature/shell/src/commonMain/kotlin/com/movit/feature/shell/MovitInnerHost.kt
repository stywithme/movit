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
        is MovitInnerRoute.ProgramDetail -> {
            ProgramDetailRoute(
                programId = route.programId,
                onBack = onBack,
                onStartProgram = { onNavigate(MovitInnerRoute.WorkoutSession(route.programId)) },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.WorkoutSession -> {
            WorkoutSessionRoute(
                workoutId = route.workoutId,
                onBack = onBack,
                onExerciseClick = { onNavigate(MovitInnerRoute.ExercisePrepare(it)) },
                onStartWorkout = { exerciseId ->
                    onNavigate(MovitInnerRoute.ExercisePrepare(exerciseId))
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.ExercisePrepare -> {
            ExercisePrepareRoute(
                exerciseId = route.exerciseId,
                onBack = onBack,
                onStart = { /* Phase 07: legacy TrainingActivity */ },
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
                onEffect = { onShellEvent(MovitAppShellEvent.LevelEffectReceived(it)) },
                modifier = modifier,
            )
        }
    }
}
