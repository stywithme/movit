package com.movit.feature.library

import androidx.compose.runtime.Composable
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
        onSeeMore = viewModel::onSeeMore,
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
        onSeeMore = viewModel::onSeeMore,
        onClearFilters = viewModel::onClearFilters,
        onItemClick = onItemClick,
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}

@Composable
fun ProgramDetailRoute(
    programId: String,
    onBack: () -> Unit,
    onStartSession: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgramDetailViewModel = viewModel { ProgramDetailViewModel(programId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    ProgramDetailScreen(
        state = state,
        onBack = onBack,
        onTabSelected = viewModel::onTabSelected,
        onWeekSelected = viewModel::onWeekSelected,
        onStartProgram = {
            scope.launch {
                viewModel.startProgramAndGetSessionKey()?.let(onStartSession)
            }
        },
        onEditReasonSelected = viewModel::onEditReasonSelected,
        onEditScopeSelected = viewModel::onEditScopeSelected,
        onWeeklyTargetChange = viewModel::onWeeklyTargetChange,
        onPauseCalendarToggle = viewModel::onPauseCalendarToggle,
        onSaveEdit = viewModel::onSaveEdit,
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}

@Composable
fun WorkoutSessionRoute(
    workoutId: String,
    onBack: () -> Unit,
    onExerciseClick: (String) -> Unit,
    onStartWorkout: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutSessionViewModel = viewModel { WorkoutSessionViewModel(workoutId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    WorkoutSessionScreen(
        state = state,
        onBack = onBack,
        onToggleEdit = viewModel::toggleEditMode,
        onExerciseClick = onExerciseClick,
        onSwapExercise = viewModel::openSwapSheet,
        onEditExercise = viewModel::openEditSheet,
        onDeleteExercise = viewModel::deleteExercise,
        onAddExercise = viewModel::openAddExerciseSheet,
        onAddRest = viewModel::addRestBlock,
        onStartWorkout = {
            if (state.session != null) onStartWorkout()
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
        onChipSelected = viewModel::onChipSelected,
        onProgramClick = onProgramClick,
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}

@Composable
fun ProgramWeekPlanRoute(
    programId: String,
    weekNumber: Int,
    onBack: () -> Unit,
    onOpenSession: (String) -> Unit,
    onViewWeeklyReport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgramWeekPlanViewModel = viewModel { ProgramWeekPlanViewModel(programId, weekNumber) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    ProgramWeekPlanScreen(
        state = state,
        onBack = onBack,
        onDayClick = { day ->
            val plan = state.weekPlan ?: return@ProgramWeekPlanScreen
            val workoutId = day.plannedWorkoutId ?: return@ProgramWeekPlanScreen
            onOpenSession(
                WorkoutSessionKeys.encode(
                    programId = plan.programId,
                    weekNumber = plan.weekNumber,
                    dayNumber = day.dayNumber,
                    plannedWorkoutId = workoutId,
                ),
            )
        },
        onOpenTodaySession = {
            val plan = state.weekPlan ?: return@ProgramWeekPlanScreen
            val today = plan.days.firstOrNull { it.status == ProgramFlowDayStatus.Today }
                ?: return@ProgramWeekPlanScreen
            val workoutId = today.plannedWorkoutId ?: return@ProgramWeekPlanScreen
            onOpenSession(
                WorkoutSessionKeys.encode(
                    programId = plan.programId,
                    weekNumber = plan.weekNumber,
                    dayNumber = today.dayNumber,
                    plannedWorkoutId = workoutId,
                ),
            )
        },
        onViewWeeklyReport = onViewWeeklyReport,
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
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}

@Composable
fun ExercisePrepareRoute(
    exerciseId: String,
    onBack: () -> Unit,
    onStart: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExercisePrepareViewModel = viewModel { ExercisePrepareViewModel(exerciseId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    ExercisePrepareScreen(
        state = state,
        onBack = onBack,
        onStart = { viewModel.legacyFileNameForStart()?.let(onStart) },
        onSkipRest = viewModel::skipRest,
        onToggleRestPause = viewModel::toggleRestPause,
        onAddRestTime = viewModel::addRestTime,
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}

@Composable
fun WorkoutCustomizeRoute(
    workoutId: String,
    onBack: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutCustomizeViewModel = viewModel { WorkoutCustomizeViewModel(workoutId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    WorkoutCustomizeScreen(
        state = state,
        restOptionsSeconds = viewModel.restOptionsSeconds,
        onBack = onBack,
        onSetsChanged = viewModel::onSetsChanged,
        onRestOptionSelected = viewModel::onRestOptionSelected,
        onStart = {
            viewModel.commitForRun()
            onStart()
        },
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}

@Composable
fun WorkoutRunRoute(
    workoutId: String,
    onBack: () -> Unit,
    onStartExercise: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutRunViewModel = viewModel { WorkoutRunViewModel(workoutId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    WorkoutRunScreen(
        state = state,
        onBack = onBack,
        onStartExercise = { viewModel.legacyFileNameForStart()?.let(onStartExercise) },
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}
