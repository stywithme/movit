package com.movit.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movit.core.data.MovitData
import kotlinx.coroutines.launch

@Composable
fun ExercisesLibraryRoute(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryListViewModel = viewModel { LibraryListViewModel(LibraryListKind.Exercises) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
        viewModel.effects.collect { effect ->
            when (effect) {
                is LibraryListEffect.OpenItem -> onItemClick(effect.itemId)
            }
        }
    }
    ExercisesLibraryScreen(
        state = state,
        onBack = onBack,
        onQueryChange = { viewModel.onEvent(LibraryListEvent.QueryChanged(it)) },
        onFilterSelected = { viewModel.onEvent(LibraryListEvent.FilterSelected(it)) },
        onFilterClick = { viewModel.onEvent(LibraryListEvent.FilterClicked) },
        onDismissFilterSheet = { viewModel.onEvent(LibraryListEvent.DismissFilterSheet) },
        onLoadMore = { viewModel.onEvent(LibraryListEvent.LoadMore) },
        onClearFilters = { viewModel.onEvent(LibraryListEvent.ClearFilters) },
        onItemClick = { viewModel.onEvent(LibraryListEvent.ItemClicked(it)) },
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}

@Composable
fun WorkoutsLibraryRoute(
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryListViewModel = viewModel { LibraryListViewModel(LibraryListKind.Workouts) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
        viewModel.effects.collect { effect ->
            when (effect) {
                is LibraryListEffect.OpenItem -> onItemClick(effect.itemId)
            }
        }
    }
    WorkoutsLibraryScreen(
        state = state,
        onBack = onBack,
        onQueryChange = { viewModel.onEvent(LibraryListEvent.QueryChanged(it)) },
        onFilterSelected = { viewModel.onEvent(LibraryListEvent.FilterSelected(it)) },
        onFilterClick = { viewModel.onEvent(LibraryListEvent.FilterClicked) },
        onDismissFilterSheet = { viewModel.onEvent(LibraryListEvent.DismissFilterSheet) },
        onLoadMore = { viewModel.onEvent(LibraryListEvent.LoadMore) },
        onClearFilters = { viewModel.onEvent(LibraryListEvent.ClearFilters) },
        onItemClick = { viewModel.onEvent(LibraryListEvent.ItemClicked(it)) },
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}

@Composable
fun ProgramDetailRoute(
    programId: String,
    initialWeekNumber: Int? = null,
    onBack: () -> Unit,
    onStartSession: (String) -> Unit,
    onOpenDaySession: (String) -> Unit,
    onViewWeeklyReport: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgramDetailViewModel = viewModel(key = "program-detail-$programId-${initialWeekNumber ?: "current"}") {
        ProgramDetailViewModel(programId = programId, initialWeekNumber = initialWeekNumber)
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProgramDetailEffect.StartSession -> onStartSession(effect.sessionKey)
                is ProgramDetailEffect.ViewWeeklyReport -> onViewWeeklyReport(effect.weekNumber)
            }
        }
    }
    if (MovitData.isInstalled) {
        LaunchedEffect(viewModel) {
            MovitData.sync.cacheInvalidated.collect {
                viewModel.load()
            }
        }
    }
    ProgramDetailScreen(
        state = state,
        onBack = onBack,
        onTabSelected = { viewModel.onEvent(ProgramDetailEvent.TabSelected(it)) },
        onWeekSelected = { viewModel.onEvent(ProgramDetailEvent.WeekSelected(it)) },
        onDaySelected = { viewModel.onEvent(ProgramDetailEvent.DaySelected(it)) },
        onOpenDaySession = onOpenDaySession,
        onStartProgram = { viewModel.onEvent(ProgramDetailEvent.StartProgramClicked) },
        onEditReasonSelected = { viewModel.onEvent(ProgramDetailEvent.EditReasonSelected(it)) },
        onEditScopeSelected = { viewModel.onEvent(ProgramDetailEvent.EditScopeSelected(it)) },
        onWeeklyTargetChange = { viewModel.onEvent(ProgramDetailEvent.WeeklyTargetChange(it)) },
        onPauseCalendarToggle = { viewModel.onEvent(ProgramDetailEvent.PauseCalendarToggle) },
        onSessionMove = { sessionId, direction ->
            viewModel.onEvent(ProgramDetailEvent.SessionMove(sessionId, direction))
        },
        onExerciseParamChange = { sessionId, exerciseId, sets, reps, weightKg, restSeconds ->
            viewModel.onEvent(
                ProgramDetailEvent.ExerciseParamChange(
                    sessionId = sessionId,
                    exerciseId = exerciseId,
                    sets = sets,
                    reps = reps,
                    weightKg = weightKg,
                    restSeconds = restSeconds,
                ),
            )
        },
        onRemoveSession = { viewModel.onEvent(ProgramDetailEvent.RemoveSession(it)) },
        onRemoveExercise = { sessionId, exerciseId ->
            viewModel.onEvent(ProgramDetailEvent.RemoveExercise(sessionId, exerciseId))
        },
        onResetEditDay = { viewModel.onEvent(ProgramDetailEvent.ResetEditDay) },
        onSaveEdit = { viewModel.onEvent(ProgramDetailEvent.SaveEdit) },
        onViewWeeklyReport = { viewModel.onEvent(ProgramDetailEvent.ViewWeeklyReportClicked) },
        onDownloadWeekOffline = { viewModel.onEvent(ProgramDetailEvent.DownloadWeekOffline) },
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}

@Composable
fun WorkoutSessionRoute(
    workoutId: String,
    onBack: () -> Unit,
    onExerciseClick: (String) -> Unit,
    onStartWorkout: (String) -> Unit,
    onSwitchWorkout: (String) -> Unit,
    onOpenCatchUpDay: (String) -> Unit,
    onSnackbar: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WorkoutSessionViewModel = viewModel { WorkoutSessionViewModel(workoutId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
        viewModel.effects.collect { effect ->
            when (effect) {
                is WorkoutSessionEffect.ShowSnackbar -> {
                    onSnackbar(effect.messageKey)
                    viewModel.onEvent(WorkoutSessionEvent.SnackbarConsumed)
                }
                is WorkoutSessionEffect.StartWorkout -> {
                    val session = viewModel.state.value.session ?: return@collect
                    val config = WorkoutFlowMapper.fromSession(session)
                    WorkoutFlowCache.put(workoutId, config)
                    WorkoutFlowCache.ensureWorkoutGroupId(workoutId)
                    WorkoutRunProgressStore.clear(workoutId)
                    onStartWorkout(effect.firstExerciseId)
                }
                is WorkoutSessionEffect.OpenExercise -> {
                    viewModel.state.value.session?.let { session ->
                        WorkoutFlowCache.put(workoutId, WorkoutFlowMapper.fromSession(session))
                    }
                    onExerciseClick(effect.exerciseId)
                }
                is WorkoutSessionEffect.SwitchWorkout -> onSwitchWorkout(effect.sessionKey)
                is WorkoutSessionEffect.OpenCatchUpDay -> onOpenCatchUpDay(effect.sessionKey)
            }
        }
    }
    WorkoutSessionScreen(
        state = state,
        onBack = onBack,
        onToggleEdit = { viewModel.onEvent(WorkoutSessionEvent.ToggleEditMode) },
        onExerciseClick = { viewModel.onEvent(WorkoutSessionEvent.OpenExerciseClicked(it)) },
        onSwapExercise = { viewModel.onEvent(WorkoutSessionEvent.OpenSwapSheet(it)) },
        onEditExercise = { viewModel.onEvent(WorkoutSessionEvent.OpenEditSheet(it)) },
        onDeleteExercise = { viewModel.onEvent(WorkoutSessionEvent.DeleteExercise(it)) },
        onAddExercise = { viewModel.onEvent(WorkoutSessionEvent.OpenAddExerciseSheet) },
        onAddRest = { viewModel.onEvent(WorkoutSessionEvent.AddRestBlock) },
        onStartWorkout = { viewModel.onEvent(WorkoutSessionEvent.StartWorkoutClicked) },
        onRetry = { scope.launch { viewModel.load() } },
        onDismissSheet = { viewModel.onEvent(WorkoutSessionEvent.DismissSheet) },
        onSwapQueryChange = { viewModel.onEvent(WorkoutSessionEvent.SwapQueryChanged(it)) },
        onSwapCandidateSelected = { viewModel.onEvent(WorkoutSessionEvent.SwapCandidateSelected(it)) },
        onAddExerciseQueryChange = { viewModel.onEvent(WorkoutSessionEvent.AddExerciseQueryChanged(it)) },
        onAddExerciseCandidateSelected = { viewModel.onEvent(WorkoutSessionEvent.AddExerciseCandidateSelected(it)) },
        onEditDraftChange = { viewModel.onEvent(WorkoutSessionEvent.EditDraftChanged(it)) },
        onSaveEditDetails = { viewModel.onEvent(WorkoutSessionEvent.SaveEditDetails) },
        onSwitchEditToSwap = { viewModel.onEvent(WorkoutSessionEvent.SwitchEditToSwap) },
        onRestClick = { viewModel.onEvent(WorkoutSessionEvent.RestClicked(it)) },
        onDeleteBlock = { viewModel.onEvent(WorkoutSessionEvent.DeleteBlock(it)) },
        onMoveBlock = { sectionRole, blockId, delta ->
            viewModel.onEvent(WorkoutSessionEvent.MoveBlock(sectionRole, blockId, delta))
        },
        onRestDurationChange = { viewModel.onEvent(WorkoutSessionEvent.RestDurationChanged(it)) },
        onSaveRestEdit = { viewModel.onEvent(WorkoutSessionEvent.SaveRestEdit) },
        onSelectPlannedWorkout = { viewModel.onEvent(WorkoutSessionEvent.SelectPlannedWorkout(it)) },
        onTogglePlannedWorkoutExpand = { viewModel.onEvent(WorkoutSessionEvent.TogglePlannedWorkoutExpand(it)) },
        onDismissCatchUpDialog = { viewModel.onEvent(WorkoutSessionEvent.DismissCatchUpDialog) },
        onOpenCatchUpDay = { viewModel.onEvent(WorkoutSessionEvent.OpenCatchUpDayClicked) },
        onSkipWarmup = { viewModel.onEvent(WorkoutSessionEvent.SkipWarmup) },
        modifier = modifier,
    )
}

@Composable
fun ProgramListRoute(
    onBack: () -> Unit,
    onProgramClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgramListViewModel = viewModel { ProgramListViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
        viewModel.effects.collect { effect ->
            when (effect) {
                is ProgramListEffect.OpenProgram -> onProgramClick(effect.programId)
            }
        }
    }
    ProgramListScreen(
        state = state,
        onBack = onBack,
        onQueryChange = { viewModel.onEvent(ProgramListEvent.QueryChanged(it)) },
        onChipSelected = { viewModel.onEvent(ProgramListEvent.ChipSelected(it)) },
        onLoadMore = { viewModel.onEvent(ProgramListEvent.LoadMore) },
        onProgramClick = { viewModel.onEvent(ProgramListEvent.ProgramClicked(it)) },
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}

@Composable
fun WeeklyReportRoute(
    programId: String,
    weekNumber: Int,
    onBack: () -> Unit,
    onEffect: (WeeklyReportEffect) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WeeklyReportViewModel = viewModel { WeeklyReportViewModel(programId, weekNumber) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
        viewModel.effects.collect(onEffect)
    }
    WeeklyReportScreen(
        state = state,
        onBack = onBack,
        onShare = { viewModel.onEvent(WeeklyReportEvent.ShareClicked) },
        onWeekSelected = { viewModel.onEvent(WeeklyReportEvent.WeekSelected(it)) },
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}

@Composable
fun ExercisePrepareRoute(
    exerciseId: String,
    onBack: () -> Unit,
    onStart: (TrainingStartAction?) -> Unit,
    modifier: Modifier = Modifier,
    workoutId: String? = null,
    prepareMode: String = "prepare",
    restSeconds: Int? = null,
    upNextExerciseId: String? = null,
    viewModel: ExercisePrepareViewModel = viewModel(
        key = "prepare-$exerciseId-${workoutId ?: "solo"}-$prepareMode-$upNextExerciseId",
    ) {
        ExercisePrepareViewModel(
            exerciseId = exerciseId,
            workoutId = workoutId,
            initialMode = if (prepareMode == "rest") {
                ExercisePrepareMode.Rest
            } else {
                ExercisePrepareMode.Prepare
            },
            initialRestSeconds = restSeconds,
            upNextExerciseId = upNextExerciseId,
        )
    },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ExercisePrepareEffect.StartTraining -> onStart(effect.action)
            }
        }
    }
    ExercisePrepareScreen(
        state = state,
        onBack = onBack,
        onStart = { viewModel.onEvent(ExercisePrepareEvent.StartClicked(workoutId = workoutId)) },
        onSkipRest = { viewModel.onEvent(ExercisePrepareEvent.SkipRest) },
        onToggleRestPause = { viewModel.onEvent(ExercisePrepareEvent.ToggleRestPause) },
        onAddRestTime = { viewModel.onEvent(ExercisePrepareEvent.AddRestTime) },
        onRetry = { scope.launch { viewModel.load() } },
        onPoseVariantSelected = { viewModel.onEvent(ExercisePrepareEvent.PoseVariantSelected(it)) },
        modifier = modifier,
    )
}
