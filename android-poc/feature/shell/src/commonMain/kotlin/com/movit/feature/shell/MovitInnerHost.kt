package com.movit.feature.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.feature.account.MovitAssessmentRoute
import com.movit.feature.account.MovitAuthRoute
import com.movit.feature.account.MovitLevelRoute
import com.movit.feature.account.MovitOnboardingRoute
import com.movit.feature.account.MovitProfileRoute
import com.movit.feature.account.MovitProfileViewModel
import com.movit.feature.library.ExercisePrepareRoute
import com.movit.feature.library.ExercisesLibraryRoute
import com.movit.feature.library.WorkoutFlowCache
import com.movit.feature.library.WorkoutRunPostNav
import com.movit.feature.library.WorkoutRunProgressStore
import com.movit.feature.library.ProgramDetailRoute
import com.movit.feature.library.ProgramListRoute
import com.movit.feature.library.WeeklyReportEffect
import com.movit.feature.library.WeeklyReportRoute
import com.movit.feature.training.ExerciseLiveRoute
import com.movit.feature.training.PlannedWorkoutContext
import com.movit.feature.training.TrainingSessionRoute
import com.movit.feature.training.TrainingSessionRouteArgs
import com.movit.feature.training.WorkoutUploadContext
import com.movit.feature.trainingdebug.TrainingDebugRoute
import com.movit.feature.trainingdebug.isTrainingDebugLabEnabled
import com.movit.feature.library.TrainingStartAction
import com.movit.feature.library.WorkoutSessionRoute
import com.movit.feature.library.WorkoutsLibraryRoute
import com.movit.feature.reports.ReportDetailEffect
import com.movit.feature.reports.ReportDetailRoute

@Composable
fun MovitInnerHost(
    route: MovitInnerRoute,
    profileViewModel: MovitProfileViewModel,
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
                    onNavigate(MovitInnerRoute.ProgramDetail(programId = programId))
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
                    if (effect is WeeklyReportEffect.ShareRequested) {
                        onShellEffect(
                            MovitAppShellEffect.ShareText(
                                subject = effect.subject,
                                text = effect.text,
                            ),
                        )
                    }
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.ProgramDetail -> {
            ProgramDetailRoute(
                programId = route.programId,
                initialWeekNumber = route.initialWeekNumber,
                onBack = onBack,
                onStartSession = { sessionKey ->
                    onNavigate(MovitInnerRoute.WorkoutSession(sessionKey))
                },
                onOpenDaySession = { sessionKey ->
                    onNavigate(MovitInnerRoute.WorkoutSession(sessionKey))
                },
                onViewWeeklyReport = { weekNumber ->
                    onNavigate(
                        MovitInnerRoute.WeeklyReport(
                            programId = route.programId,
                            weekNumber = weekNumber,
                        ),
                    )
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
                onStartWorkout = { exerciseId ->
                    onNavigate(
                        MovitInnerRoute.ExercisePrepare(
                            exerciseId = exerciseId,
                            workoutId = route.workoutId,
                        ),
                    )
                },
                onSwitchWorkout = { sessionKey ->
                    onNavigate(MovitInnerRoute.WorkoutSession(sessionKey))
                },
                onOpenCatchUpDay = { sessionKey ->
                    onNavigate(MovitInnerRoute.WorkoutSession(sessionKey))
                },
                onSnackbar = { key ->
                    onShellEffect(MovitAppShellEffect.ShowLocalizedMessage(key))
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
                onFinish = { _ ->
                    val workoutId = route.workoutId
                    if (workoutId != null) {
                        onNavigate(MovitInnerRoute.WorkoutSession(workoutId))
                    } else {
                        onBack()
                    }
                },
                onViewReport = { reportId ->
                    onNavigate(MovitInnerRoute.ReportDetail(reportId))
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.TrainingSession -> {
            TrainingSessionRoute(
                args = TrainingSessionRouteArgs(
                    exerciseSlug = route.exerciseSlug,
                    exerciseName = route.exerciseName,
                    targetReps = route.targetReps,
                    workoutId = route.workoutId,
                    flowItems = route.flowItems,
                    startExerciseIndex = route.startExerciseIndex,
                    poseVariantIndex = route.poseVariantIndex,
                    uploadContext = route.plannedWorkout?.let { planned ->
                        WorkoutUploadContext(
                            workoutGroupId = planned.plannedWorkoutId,
                            context = "program",
                        )
                    },
                    plannedWorkout = route.plannedWorkout?.let { planned ->
                        PlannedWorkoutContext(
                            plannedWorkoutId = planned.plannedWorkoutId,
                            programId = planned.programId,
                            weekNumber = planned.weekNumber,
                            dayNumber = planned.dayNumber,
                        )
                    },
                ),
                onBack = onBack,
                onFinish = { isWorkoutFlowComplete ->
                    handleWorkoutTrainingFinish(
                        workoutId = route.workoutId,
                        completedExerciseIndex = route.startExerciseIndex,
                        isWorkoutFlowComplete = isWorkoutFlowComplete,
                        onNavigate = onNavigate,
                        onBack = onBack,
                    )
                },
                onViewReport = { reportId ->
                    onNavigate(MovitInnerRoute.ReportDetail(reportId))
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.ExercisePrepare -> {
            ExercisePrepareRoute(
                exerciseId = route.exerciseId,
                workoutId = route.workoutId,
                prepareMode = route.prepareMode,
                restSeconds = route.restSeconds,
                upNextExerciseId = route.upNextExerciseId,
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
                    when (effect) {
                        is ReportDetailEffect.ShareRequested,
                        is ReportDetailEffect.ExportRequested -> {
                            val payload = when (effect) {
                                is ReportDetailEffect.ShareRequested -> effect.payload
                                is ReportDetailEffect.ExportRequested -> effect.payload
                            }
                            onShellEffect(
                                MovitAppShellEffect.ShareText(
                                    subject = payload.chooserTitleKey,
                                    text = payload.text,
                                ),
                            )
                        }
                    }
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
        MovitInnerRoute.Profile -> {
            MovitProfileRoute(
                viewModel = profileViewModel,
                onBack = onBack,
                onEffect = { onShellEvent(MovitAppShellEvent.ProfileEffectReceived(it)) },
                modifier = modifier,
            )
        }
        MovitInnerRoute.ProfileOnboarding -> {
            MovitOnboardingRoute(
                onEffect = { onShellEvent(MovitAppShellEvent.OnboardingEffectReceived(it)) },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.Assessment -> {
            MovitAssessmentRoute(
                assessmentMode = route.mode,
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
        is MovitInnerRoute.TrainingDebugLab -> {
            if (isTrainingDebugLabEnabled()) {
                TrainingDebugRoute(
                    exerciseSlug = route.exerciseSlug,
                    onBack = onBack,
                    onCopy = { text ->
                        onShellEffect(
                            MovitAppShellEffect.ShareText(
                                subject = "Training Debug Lab",
                                text = text,
                            ),
                        )
                    },
                    modifier = modifier,
                )
            } else {
                onBack()
            }
        }
    }
}

private fun handleWorkoutTrainingFinish(
    workoutId: String?,
    completedExerciseIndex: Int,
    isWorkoutFlowComplete: Boolean,
    onNavigate: (MovitInnerRoute) -> Unit,
    onBack: () -> Unit,
) {
    if (workoutId == null) {
        onBack()
        return
    }
    val config = WorkoutFlowCache.get(workoutId)
    when (
        val nav = WorkoutRunProgressStore.onTrainingSessionFinish(
            workoutId = workoutId,
            config = config,
            completedExerciseIndex = completedExerciseIndex,
            isWorkoutFlowComplete = isWorkoutFlowComplete,
        )
    ) {
        WorkoutRunPostNav.BackToRun,
        WorkoutRunPostNav.Complete,
        -> onNavigate(MovitInnerRoute.WorkoutSession(workoutId))
        is WorkoutRunPostNav.Rest -> onNavigate(
            MovitInnerRoute.ExercisePrepare(
                exerciseId = nav.restingExerciseId,
                workoutId = workoutId,
                prepareMode = "rest",
                restSeconds = nav.restSeconds,
                upNextExerciseId = nav.upNextExerciseId,
            ),
        )
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
                    flowItems = action.flowItems,
                    plannedWorkout = action.plannedWorkout,
                    startExerciseIndex = action.startExerciseIndex,
                    poseVariantIndex = action.poseVariantIndex,
                ),
            )
        }
        is TrainingStartAction.Legacy,
        null,
        -> {
            onShellEffect(
                MovitAppShellEffect.ShowLocalizedMessage("training_config_first_use_online"),
            )
        }
    }
}
