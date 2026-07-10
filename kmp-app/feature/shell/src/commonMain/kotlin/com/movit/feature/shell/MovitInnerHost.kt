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
import com.movit.feature.library.LaunchSource
import com.movit.feature.library.ReturnTarget
import com.movit.feature.library.WorkoutFlowCache
import com.movit.feature.library.WorkoutLaunchCoordinator
import com.movit.feature.library.WorkoutLaunchRequest
import com.movit.feature.library.WorkoutRunFinishNav
import com.movit.feature.library.WorkoutRunStore
import com.movit.feature.library.WorkoutSessionKeys
import com.movit.feature.library.resolveWorkoutRunFinish
import com.movit.feature.library.ProgramDetailRoute
import com.movit.feature.library.ProgramListRoute
import com.movit.feature.library.WeeklyReportEffect
import com.movit.feature.library.WeeklyReportRoute
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
                onItemClick = { workoutId ->
                    WorkoutLaunchCoordinator.remember(
                        WorkoutLaunchCoordinator.fromExploreWorkout(workoutId),
                    )
                    onNavigate(MovitInnerRoute.WorkoutSession(workoutId))
                },
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
                    rememberProgramWorkoutLaunch(
                        sessionKey = sessionKey,
                        priorSource = LaunchSource.Program,
                    )
                    onNavigate(MovitInnerRoute.WorkoutSession(sessionKey))
                },
                onOpenDaySession = { sessionKey ->
                    rememberProgramWorkoutLaunch(
                        sessionKey = sessionKey,
                        priorSource = LaunchSource.Program,
                    )
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
                onExerciseClick = { exerciseId, runDraftId ->
                    onNavigate(
                        MovitInnerRoute.ExercisePrepare(
                            exerciseId = exerciseId,
                            workoutId = route.workoutId,
                            prepareMode = com.movit.feature.library.ExercisePrepareModeCodec.PREVIEW,
                            runId = runDraftId,
                        ),
                    )
                },
                onStartWorkout = { exerciseId, runId ->
                    onNavigate(
                        MovitInnerRoute.ExercisePrepare(
                            exerciseId = exerciseId,
                            workoutId = route.workoutId,
                            prepareMode = com.movit.feature.library.ExercisePrepareModeCodec.WORKOUT_FIRST,
                            runId = runId,
                        ),
                    )
                },
                onSwitchWorkout = { sessionKey ->
                    rememberProgramWorkoutLaunch(
                        sessionKey = sessionKey,
                        priorSource = WorkoutLaunchCoordinator.peek(route.workoutId)?.source
                            ?: LaunchSource.Program,
                    )
                    onNavigate(MovitInnerRoute.WorkoutSession(sessionKey))
                },
                onOpenCatchUpDay = { sessionKey ->
                    rememberProgramWorkoutLaunch(
                        sessionKey = sessionKey,
                        priorSource = WorkoutLaunchCoordinator.peek(route.workoutId)?.source
                            ?: LaunchSource.Train,
                    )
                    onNavigate(MovitInnerRoute.WorkoutSession(sessionKey))
                },
                onSnackbar = { key ->
                    onShellEffect(MovitAppShellEffect.ShowLocalizedMessage(key))
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.TrainingSession -> {
            androidx.compose.runtime.DisposableEffect(route) {
                com.movit.core.data.sync.TrainingSessionSyncGate.trainingSessionActive = true
                onDispose {
                    com.movit.core.data.sync.TrainingSessionSyncGate.trainingSessionActive = false
                }
            }
            TrainingSessionRoute(
                args = TrainingSessionRouteArgs(
                    exerciseSlug = route.exerciseSlug,
                    exerciseName = route.exerciseName,
                    targetReps = route.targetReps,
                    workoutId = route.workoutId,
                    flowItems = route.flowItems,
                    startExerciseIndex = route.startExerciseIndex,
                    poseVariantIndex = route.poseVariantIndex,
                    runId = route.runId,
                    uploadContext = resolveTrainingUploadContext(route),
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
                    if (isWorkoutFlowComplete) {
                        // Report navigation replaces the journey; Finish is a no-op for complete runs.
                        return@TrainingSessionRoute
                    }
                    handleWorkoutTrainingFinish(
                        workoutId = route.workoutId,
                        isWorkoutFlowComplete = false,
                        onNavigate = onNavigate,
                        onBack = onBack,
                    )
                },
                onViewReport = { reportId ->
                    val run = route.runId?.let { WorkoutRunStore.get(it) }
                    onShellEvent(
                        MovitAppShellEvent.ReplaceWorkoutJourneyWithReport(
                            reportId = reportId,
                            returnTarget = run?.returnTarget,
                            doneTarget = run?.doneTarget,
                        ),
                    )
                },
                onExitWorkoutJourney = {
                    onShellEvent(MovitAppShellEvent.ExitWorkoutJourney)
                },
                modifier = modifier,
            )
        }
        is MovitInnerRoute.ExercisePrepare -> {
            ExercisePrepareRoute(
                exerciseId = route.exerciseId,
                workoutId = route.workoutId,
                prepareMode = route.prepareMode,
                runId = route.runId,
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
                onBack = {
                    when (route.returnTarget) {
                        ReturnTarget.Train,
                        ReturnTarget.Explore,
                        is ReturnTarget.ProgramDetail,
                        -> onShellEvent(
                            MovitAppShellEvent.NavigateReportReturn(
                                target = route.returnTarget,
                                clearInner = true,
                            ),
                        )
                        is ReturnTarget.WorkoutSession,
                        null,
                        -> onBack()
                    }
                },
                onDone = {
                    onShellEvent(
                        MovitAppShellEvent.NavigateReportReturn(
                            target = route.doneTarget ?: route.returnTarget ?: ReturnTarget.Explore,
                            clearInner = true,
                        ),
                    )
                },
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

private fun rememberProgramWorkoutLaunch(
    sessionKey: String,
    priorSource: LaunchSource,
): String {
    val parsed = WorkoutSessionKeys.parse(sessionKey)
    val request: WorkoutLaunchRequest = when {
        parsed != null && priorSource == LaunchSource.Train ->
            WorkoutLaunchCoordinator.fromTrainProgramDay(
                programId = parsed.programId,
                weekNumber = parsed.weekNumber,
                dayNumber = parsed.dayNumber,
                plannedWorkoutId = parsed.plannedWorkoutId,
            )
        parsed != null && priorSource == LaunchSource.Program ->
            WorkoutLaunchCoordinator.fromProgramDetail(
                programId = parsed.programId,
                weekNumber = parsed.weekNumber,
                dayNumber = parsed.dayNumber,
                plannedWorkoutId = parsed.plannedWorkoutId,
            )
        parsed != null ->
            WorkoutLaunchCoordinator.fromSessionKey(
                sessionWorkoutId = sessionKey,
                source = priorSource,
                returnTarget = ReturnTarget.ProgramDetail(
                    programId = parsed.programId,
                    weekNumber = parsed.weekNumber,
                ),
            )
        else ->
            WorkoutLaunchCoordinator.fromSessionKey(
                sessionWorkoutId = sessionKey,
                source = priorSource,
                returnTarget = ReturnTarget.Train,
            )
    }
    return WorkoutLaunchCoordinator.remember(request)
}

private fun resolveTrainingUploadContext(route: MovitInnerRoute.TrainingSession): WorkoutUploadContext? {
    route.plannedWorkout?.let { planned ->
        return WorkoutUploadContext(
            workoutGroupId = route.runId ?: planned.plannedWorkoutId,
            context = "program",
        )
    }
    val workoutId = route.workoutId
    if (route.flowItems != null && !workoutId.isNullOrBlank()) {
        val groupId = route.runId
            ?: WorkoutRunStore.activeForWorkout(workoutId)?.workoutGroupId
            ?: WorkoutFlowCache.workoutGroupIdOrNull(workoutId)
            ?: WorkoutFlowCache.ensureWorkoutGroupId(workoutId)
        return WorkoutUploadContext(
            workoutGroupId = groupId,
            workoutTemplateId = workoutId,
        )
    }
    return null
}

private fun handleWorkoutTrainingFinish(
    workoutId: String?,
    isWorkoutFlowComplete: Boolean,
    onNavigate: (MovitInnerRoute) -> Unit,
    onBack: () -> Unit,
) {
    if (workoutId == null) {
        onBack()
        return
    }
    when (resolveWorkoutRunFinish(workoutId, isWorkoutFlowComplete)) {
        WorkoutRunFinishNav.BackToSession,
        WorkoutRunFinishNav.Complete,
        -> onNavigate(MovitInnerRoute.WorkoutSession(workoutId))
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
                    runId = action.runId,
                ),
            )
        }
        null -> {
            onShellEffect(
                MovitAppShellEffect.ShowLocalizedMessage("training_config_first_use_online"),
            )
        }
    }
}
