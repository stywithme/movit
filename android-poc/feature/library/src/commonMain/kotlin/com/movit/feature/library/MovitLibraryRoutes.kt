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
        onChipSelected = viewModel::onChipSelected,
        onSeeMore = viewModel::onSeeMore,
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
        onChipSelected = viewModel::onChipSelected,
        onSeeMore = viewModel::onSeeMore,
        onItemClick = onItemClick,
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}

@Composable
fun ProgramDetailRoute(
    programId: String,
    onBack: () -> Unit,
    onStartProgram: () -> Unit,
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
        onStartProgram = onStartProgram,
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}

@Composable
fun WorkoutSessionRoute(
    workoutId: String,
    onBack: () -> Unit,
    onExerciseClick: (String) -> Unit,
    onStartWorkout: (exerciseId: String) -> Unit,
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
        onAddRest = viewModel::addRestBlock,
        onStartWorkout = {
            state.session?.firstExerciseSlug()?.let(onStartWorkout)
        },
        onRetry = { scope.launch { viewModel.load() } },
        onDismissSheet = viewModel::dismissSheet,
        onSwapQueryChange = viewModel::onSwapQueryChange,
        onSwapCandidateSelected = viewModel::selectSwapCandidate,
        onEditDraftChange = viewModel::updateEditDraft,
        onSaveEditDetails = viewModel::saveEditDetails,
        onSwitchEditToSwap = viewModel::switchEditSheetToSwap,
        modifier = modifier,
    )
}

@Composable
fun ExercisePrepareRoute(
    exerciseId: String,
    onBack: () -> Unit,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExercisePrepareViewModel = viewModel { ExercisePrepareViewModel(exerciseId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) { viewModel.loadInitial() }
    ExercisePrepareScreen(
        state = state,
        onBack = onBack,
        onStart = onStart,
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}
