package com.movit.feature.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitInnerPageHeader
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
                onStartWorkout = {
                    onNavigate(MovitInnerRoute.ExercisePrepare("ex-squat-warm"))
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.ExercisePrepare -> {
            ExercisePrepareRoute(
                exerciseId = route.exerciseId,
                onBack = onBack,
                onStart = { /* training flow opens from legacy activity later */ },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.ReportDetail -> {
            ReportDetailRoute(
                reportId = route.reportId,
                onBack = onBack,
                onEffect = { effect ->
                    val message = when (effect) {
                        ReportDetailEffect.ShareRequested -> "Share report — coming soon."
                        ReportDetailEffect.ExportRequested -> "Export report — coming soon."
                    }
                    onShellEffect(MovitAppShellEffect.ShowMessage(message))
                },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun InnerPlaceholderScreen(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MovitInnerPageHeader(
                onBack = onBack,
                backLabel = "Back",
                title = title,
                modifier = Modifier.padding(horizontal = MovitSpacing.lg, vertical = MovitSpacing.sm),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
