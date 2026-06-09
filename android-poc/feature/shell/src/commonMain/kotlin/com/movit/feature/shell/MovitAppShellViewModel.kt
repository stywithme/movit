package com.movit.feature.shell

import androidx.lifecycle.ViewModel
import com.movit.feature.explore.ExploreItemType
import com.movit.feature.explore.MovitExploreEffect
import com.movit.feature.home.MovitHomeEffect
import com.movit.feature.reports.MovitReportsEffect
import com.movit.feature.train.MovitTrainEffect
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MovitAppShellViewModel : ViewModel() {
    private val _state = MutableStateFlow(MovitAppShellState())
    val state: StateFlow<MovitAppShellState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<MovitAppShellEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitAppShellEffect> = _effects.asSharedFlow()

    fun emitShellEffect(effect: MovitAppShellEffect) {
        _effects.tryEmit(effect)
    }

    fun onEvent(event: MovitAppShellEvent) {
        when (event) {
            is MovitAppShellEvent.DestinationSelected -> {
                _state.update { it.copy(selectedDestination = event.destination) }
            }
            is MovitAppShellEvent.InnerRoutePushed -> pushInner(event.route)
            MovitAppShellEvent.InnerRoutePopped -> popInner()
            is MovitAppShellEvent.ExploreEffectReceived -> handleExploreEffect(event.effect)
            is MovitAppShellEvent.ExploreItemSelected -> {
                pushInner(MovitInnerRoute.ExercisePrepare(event.itemId))
            }
            is MovitAppShellEvent.HomeEffectReceived -> {
                handleHomeEffect(event.effect)
            }
            is MovitAppShellEvent.TrainEffectReceived -> {
                handleTrainEffect(event.effect)
            }
            is MovitAppShellEvent.ReportsEffectReceived -> {
                handleReportsEffect(event.effect)
            }
            is MovitAppShellEvent.HeaderUserNameUpdated -> {
                _state.update { it.copy(headerUserName = event.userName) }
            }
        }
    }

    private fun handleHomeEffect(effect: MovitHomeEffect) {
        when (effect) {
            MovitHomeEffect.OpenTrain -> navigateTo(MovitAppDestination.Train)
            MovitHomeEffect.OpenExplore -> navigateTo(MovitAppDestination.Explore)
            MovitHomeEffect.OpenReports -> navigateTo(MovitAppDestination.Reports)
            MovitHomeEffect.OpenProfile -> navigateTo(MovitAppDestination.Profile)
            is MovitHomeEffect.ShowMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowMessage(effect.message))
            }
        }
    }

    private fun handleReportsEffect(effect: MovitReportsEffect) {
        when (effect) {
            MovitReportsEffect.OpenTrain -> navigateTo(MovitAppDestination.Train)
            MovitReportsEffect.OpenUpgrade -> {
                _effects.tryEmit(
                    MovitAppShellEffect.ShowMessage("Subscription upgrade opens from Profile."),
                )
            }
            is MovitReportsEffect.OpenReportDetail -> {
                pushInner(MovitInnerRoute.ReportDetail(effect.reportId))
            }
            is MovitReportsEffect.ShowMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowMessage(effect.message))
            }
        }
    }

    private fun handleTrainEffect(effect: MovitTrainEffect) {
        when (effect) {
            MovitTrainEffect.OpenExplore -> navigateTo(MovitAppDestination.Explore)
            MovitTrainEffect.OpenReports -> navigateTo(MovitAppDestination.Reports)
            is MovitTrainEffect.OpenProgramWorkout -> {
                pushInner(
                    MovitInnerRoute.WorkoutSession(
                        com.movit.feature.library.WorkoutSessionKeys.encode(
                            programId = effect.target.programId,
                            weekNumber = effect.target.weekNumber,
                            dayNumber = effect.target.dayNumber,
                            plannedWorkoutId = effect.target.plannedWorkoutId,
                        ),
                    ),
                )
            }
            MovitTrainEffect.OpenSessionPreview -> {
                pushInner(MovitInnerRoute.WorkoutSession("preview"))
            }
            is MovitTrainEffect.ShowMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowMessage(effect.message))
            }
        }
    }

    private fun handleExploreEffect(effect: MovitExploreEffect) {
        when (effect) {
            MovitExploreEffect.OpenExercisesLibrary -> pushInner(MovitInnerRoute.ExercisesLibrary)
            MovitExploreEffect.OpenWorkoutsLibrary -> pushInner(MovitInnerRoute.WorkoutsLibrary)
            is MovitExploreEffect.OpenProgramDetail -> pushInner(MovitInnerRoute.ProgramDetail(effect.programId))
            is MovitExploreEffect.OpenWorkoutSession -> pushInner(MovitInnerRoute.WorkoutSession(effect.workoutId))
            is MovitExploreEffect.OpenExercisePrepare -> pushInner(MovitInnerRoute.ExercisePrepare(effect.exerciseId))
            is MovitExploreEffect.NavigateToItem -> {
                when (effect.type) {
                    ExploreItemType.Exercise -> pushInner(MovitInnerRoute.ExercisePrepare(effect.id))
                    ExploreItemType.Workout -> pushInner(MovitInnerRoute.WorkoutSession(effect.id))
                    ExploreItemType.Program -> pushInner(MovitInnerRoute.ProgramDetail(effect.id))
                }
            }
            is MovitExploreEffect.NavigateToExercise -> {
                pushInner(MovitInnerRoute.ExercisePrepare(effect.id))
            }
            is MovitExploreEffect.ShowMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowMessage(effect.message))
            }
        }
    }

    private fun pushInner(route: MovitInnerRoute) {
        _state.update { it.copy(innerStack = it.innerStack + route) }
    }

    private fun popInner() {
        _state.update {
            if (it.innerStack.isEmpty()) it else it.copy(innerStack = it.innerStack.dropLast(1))
        }
    }

    private fun navigateTo(destination: MovitAppDestination) {
        _state.update { it.copy(selectedDestination = destination) }
    }
}
