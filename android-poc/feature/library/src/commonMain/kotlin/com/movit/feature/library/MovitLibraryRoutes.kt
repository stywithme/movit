package com.movit.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    ExercisesLibraryScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onFilterSelected = viewModel::onFilterSelected,
        onFilterClick = viewModel::onFilterClick,
        onDismissFilterSheet = viewModel::onDismissFilterSheet,
        onLoadMore = viewModel::onLoadMore,
        onClearFilters = viewModel::onClearFilters,
        onItemClick = onItemClick,
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
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    WorkoutsLibraryScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onFilterSelected = viewModel::onFilterSelected,
        onFilterClick = viewModel::onFilterClick,
        onDismissFilterSheet = viewModel::onDismissFilterSheet,
        onLoadMore = viewModel::onLoadMore,
        onClearFilters = viewModel::onClearFilters,
        onItemClick = onItemClick,
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
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    ProgramDetailScreen(
        state = state,
        onBack = onBack,
        onTabSelected = viewModel::onTabSelected,
        onWeekSelected = viewModel::onWeekSelected,
        onDaySelected = viewModel::onDaySelected,
        onOpenDaySession = onOpenDaySession,
        onStartProgram = {
            scope.launch {
                viewModel.startProgramAndGetSessionKey()?.let(onStartSession)
            }
        },
        onEditReasonSelected = viewModel::onEditReasonSelected,
        onEditScopeSelected = viewModel::onEditScopeSelected,
        onWeeklyTargetChange = viewModel::onWeeklyTargetChange,
        onPauseCalendarToggle = viewModel::onPauseCalendarToggle,
        onSessionMove = viewModel::onSessionMove,
        onExerciseParamChange = viewModel::onExerciseParamChange,
        onRemoveSession = viewModel::onRemoveSession,
        onRemoveExercise = viewModel::onRemoveExercise,
        onResetEditDay = viewModel::onResetEditDay,
        onSaveEdit = viewModel::onSaveEdit,
        onViewWeeklyReport = { onViewWeeklyReport(state.selectedWeekNumber) },
        onDownloadWeekOffline = viewModel::onDownloadWeekOffline,
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
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    LaunchedEffect(state.snackbarMessageKey) {
        state.snackbarMessageKey?.let { key ->
            onSnackbar(key)
            viewModel.consumeSnackbar()
        }
    }
    WorkoutSessionScreen(
        state = state,
        onBack = onBack,
        onToggleEdit = viewModel::toggleEditMode,
        onExerciseClick = { exerciseId ->
            state.session?.let { session ->
                WorkoutFlowCache.put(workoutId, WorkoutFlowMapper.fromSession(session))
            }
            onExerciseClick(exerciseId)
        },
        onSwapExercise = viewModel::openSwapSheet,
        onEditExercise = viewModel::openEditSheet,
        onDeleteExercise = viewModel::deleteExercise,
        onAddExercise = viewModel::openAddExerciseSheet,
        onAddRest = viewModel::addRestBlock,
        onStartWorkout = {
            val session = state.session ?: return@WorkoutSessionScreen
            val config = WorkoutFlowMapper.fromSession(session)
            val firstExercise = config.exercises.firstOrNull() ?: return@WorkoutSessionScreen
            WorkoutFlowCache.put(workoutId, config)
            WorkoutRunProgressStore.clear(workoutId)
            onStartWorkout(firstExercise.id)
        },
        onRetry = { scope.launch { viewModel.load() } },
        onDismissSheet = viewModel::dismissSheet,
        onSwapQueryChange = viewModel::onSwapQueryChange,
        onSwapCandidateSelected = viewModel::selectSwapCandidate,
        onAddExerciseQueryChange = viewModel::onAddExerciseQueryChange,
        onAddExerciseCandidateSelected = viewModel::selectAddExerciseCandidate,
        onEditDraftChange = viewModel::updateEditDraft,
        onSaveEditDetails = viewModel::saveEditDetails,
        onSwitchEditToSwap = viewModel::switchEditSheetToSwap,
        onRestClick = viewModel::onRestClick,
        onDeleteBlock = viewModel::deleteBlock,
        onMoveBlock = viewModel::moveBlock,
        onRestDurationChange = viewModel::updateRestDuration,
        onSaveRestEdit = viewModel::saveRestEdit,
        onSelectPlannedWorkout = { plannedWorkoutId ->
            val context = state.session?.context ?: return@WorkoutSessionScreen
            if (plannedWorkoutId == context.plannedWorkoutId) return@WorkoutSessionScreen
            onSwitchWorkout(
                WorkoutSessionKeys.encode(
                    programId = context.programId,
                    weekNumber = context.weekNumber,
                    dayNumber = context.dayNumber,
                    plannedWorkoutId = plannedWorkoutId,
                ),
            )
        },
        onTogglePlannedWorkoutExpand = viewModel::togglePlannedWorkoutExpand,
        onDismissCatchUpDialog = viewModel::dismissCatchUpDialog,
        onOpenCatchUpDay = {
            scope.launch {
                viewModel.sessionKeyForCatchUpDay()?.let(onOpenCatchUpDay)
            }
        },
        onSkipWarmup = viewModel::skipWarmup,
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
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    ProgramListScreen(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onChipSelected = viewModel::onChipSelected,
        onLoadMore = viewModel::onLoadMore,
        onProgramClick = onProgramClick,
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
        onShare = viewModel::onShareClicked,
        onWeekSelected = { selectedWeek ->
            viewModel.onWeekSelected(selectedWeek)
            scope.launch { viewModel.load() }
        },
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
        viewModel.startEffects.collect(onStart)
    }
    ExercisePrepareScreen(
        state = state,
        onBack = onBack,
        onStart = { viewModel.requestTrainingStart(workoutId = workoutId) },
        onSkipRest = viewModel::skipRest,
        onToggleRestPause = viewModel::toggleRestPause,
        onAddRestTime = viewModel::addRestTime,
        onRetry = { scope.launch { viewModel.load() } },
        onPoseVariantSelected = viewModel::selectPoseVariant,
        modifier = modifier,
    )
}

