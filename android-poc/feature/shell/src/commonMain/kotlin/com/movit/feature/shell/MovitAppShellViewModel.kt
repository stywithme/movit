package com.movit.feature.shell

import androidx.lifecycle.ViewModel
import com.movit.core.data.MovitData
import com.movit.feature.account.AuthBootstrapContext
import com.movit.feature.account.AuthBootstrapTarget
import com.movit.feature.account.MovitAssessmentEffect
import com.movit.feature.account.MovitAuthEffect
import com.movit.feature.account.MovitAuthViewModel
import com.movit.feature.account.MovitLevelEffect
import com.movit.feature.account.MovitOnboardingEffect
import com.movit.feature.account.MovitProfileEffect
import com.movit.core.model.ExploreItemType
import com.movit.feature.explore.MovitExploreEffect
import com.movit.feature.home.MovitHomeEffect
import com.movit.feature.reports.MovitReportsEffect
import com.movit.feature.train.MovitTrainEffect
import com.movit.shared.PlatformInfo
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

    init {
        if (MovitData.isInstalled) {
            MovitData.onSessionExpired = {
                popAllInner()
                pushInner(MovitInnerRoute.Auth)
            }
            val platform = MovitData.requirePlatform()
            val bootstrap = AuthBootstrapContext.fromMovitData()
            _state.update {
                it.copy(
                    themeMode = platform.themeMode(),
                    innerStack = resolveStartupInnerStack(
                        bootstrap = bootstrap,
                        onboardingCompleted = platform.isOnboardingCompleted(),
                    ),
                )
            }
        }
    }

    private val _effects = MutableSharedFlow<MovitAppShellEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<MovitAppShellEffect> = _effects.asSharedFlow()

    fun emitShellEffect(effect: MovitAppShellEffect) {
        _effects.tryEmit(effect)
    }

    /**
     * Handles system back. Returns true when the event was consumed (do not finish the activity).
     */
    fun handleSystemBack(): Boolean {
        val current = _state.value
        return when {
            current.innerStack.isNotEmpty() -> {
                popInner()
                true
            }
            current.selectedDestination != MovitAppDestination.Home -> {
                navigateTo(MovitAppDestination.Home)
                true
            }
            else -> false
        }
    }

    fun onEvent(event: MovitAppShellEvent) {
        when (event) {
            MovitAppShellEvent.BackPressed -> handleSystemBack()
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
            is MovitAppShellEvent.ProfileEffectReceived -> handleProfileEffect(event.effect)
            is MovitAppShellEvent.AuthEffectReceived -> handleAuthEffect(event.effect)
            is MovitAppShellEvent.OnboardingEffectReceived -> handleOnboardingEffect(event.effect)
            is MovitAppShellEvent.AssessmentEffectReceived -> handleAssessmentEffect(event.effect)
            is MovitAppShellEvent.LevelEffectReceived -> handleLevelEffect(event.effect)
        }
    }

    private fun handleHomeEffect(effect: MovitHomeEffect) {
        when (effect) {
            MovitHomeEffect.OpenTrain -> navigateTo(MovitAppDestination.Train)
            MovitHomeEffect.OpenExplore -> navigateTo(MovitAppDestination.Explore)
            MovitHomeEffect.OpenReports -> navigateTo(MovitAppDestination.Reports)
            MovitHomeEffect.OpenProfile -> navigateTo(MovitAppDestination.Profile)
            MovitHomeEffect.OpenAssessment -> pushInner(MovitInnerRoute.Assessment)
            MovitHomeEffect.OpenLevel -> pushInner(MovitInnerRoute.LevelProfile)
            is MovitHomeEffect.OpenReportDetail -> pushInner(MovitInnerRoute.ReportDetail(effect.reportId))
            is MovitHomeEffect.ShowMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowMessage(effect.message))
            }
        }
    }

    private fun handleProfileEffect(effect: MovitProfileEffect) {
        when (effect) {
            MovitProfileEffect.OpenAuth -> pushInner(MovitInnerRoute.Auth)
            MovitProfileEffect.OpenOnboarding -> pushInner(MovitInnerRoute.ProfileOnboarding)
            MovitProfileEffect.OpenAssessment -> pushInner(MovitInnerRoute.Assessment)
            MovitProfileEffect.OpenLevel -> pushInner(MovitInnerRoute.LevelProfile)
            MovitProfileEffect.OpenSubscription -> {
                if (!PlatformInfo.supportsInAppSubscription) {
                    _effects.tryEmit(
                        MovitAppShellEffect.ShowLocalizedMessage("profile_subscription_ios_unavailable"),
                    )
                }
            }
            is MovitProfileEffect.ShowLocalizedMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowLocalizedMessage(effect.key))
            }
            MovitProfileEffect.LoggedOut -> {
                popAllInner()
                pushInner(MovitInnerRoute.Auth)
            }
            is MovitProfileEffect.LanguageChanged -> {
                _state.update { it.copy(localeRevision = it.localeRevision + 1) }
            }
            is MovitProfileEffect.ThemeModeChanged -> {
                _state.update { it.copy(themeMode = effect.themeMode) }
            }
            is MovitProfileEffect.ShowMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowMessage(effect.message))
            }
        }
    }

    private fun handleAuthEffect(effect: MovitAuthEffect) {
        when (effect) {
            MovitAuthEffect.OpenShell -> popInner()
            MovitAuthEffect.OpenOnboarding -> {
                popInner()
                pushInner(MovitInnerRoute.ProfileOnboarding)
            }
            is MovitAuthEffect.ShowMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowMessage(effect.message))
            }
            is MovitAuthEffect.ShowLocalizedMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowLocalizedMessage(effect.key))
            }
        }
    }

    private fun handleOnboardingEffect(effect: MovitOnboardingEffect) {
        when (effect) {
            MovitOnboardingEffect.Completed -> popInner()
            is MovitOnboardingEffect.ShowMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowMessage(effect.message))
            }
        }
    }

    private fun handleAssessmentEffect(effect: MovitAssessmentEffect) {
        when (effect) {
            MovitAssessmentEffect.NavigateBack -> popInner()
            MovitAssessmentEffect.OpenExplore -> {
                popAllInner()
                navigateTo(MovitAppDestination.Explore)
            }
            MovitAssessmentEffect.OpenHome -> {
                popAllInner()
                navigateTo(MovitAppDestination.Home)
            }
            is MovitAssessmentEffect.ShowLocalizedMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowLocalizedMessage(effect.key))
            }
        }
    }

    private fun handleLevelEffect(effect: MovitLevelEffect) {
        when (effect) {
            MovitLevelEffect.OpenAssessment -> pushInner(MovitInnerRoute.Assessment)
            MovitLevelEffect.OpenExplore -> {
                popAllInner()
                navigateTo(MovitAppDestination.Explore)
            }
            is MovitLevelEffect.ShowMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowMessage(effect.message))
            }
        }
    }

    private fun handleReportsEffect(effect: MovitReportsEffect) {
        when (effect) {
            MovitReportsEffect.OpenTrain -> navigateTo(MovitAppDestination.Train)
            MovitReportsEffect.OpenUpgrade -> {
                navigateTo(MovitAppDestination.Profile)
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
            MovitTrainEffect.OpenProgramList -> pushInner(MovitInnerRoute.ProgramList)
            is MovitTrainEffect.OpenProgramWeekPlan -> pushInner(
                MovitInnerRoute.ProgramWeekPlan(
                    programId = effect.programId,
                    weekNumber = effect.weekNumber,
                ),
            )
            is MovitTrainEffect.OpenWeeklyReport -> pushInner(
                MovitInnerRoute.WeeklyReport(
                    programId = effect.programId,
                    weekNumber = effect.weekNumber,
                ),
            )
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
            MovitExploreEffect.OpenProgramList -> pushInner(MovitInnerRoute.ProgramList)
            is MovitExploreEffect.OpenProgramDetail -> pushInner(
                MovitInnerRoute.ProgramWeekPlan(programId = effect.programId, weekNumber = 1),
            )
            is MovitExploreEffect.OpenWorkoutSession -> pushInner(MovitInnerRoute.WorkoutSession(effect.workoutId))
            is MovitExploreEffect.OpenExercisePrepare -> pushInner(MovitInnerRoute.ExercisePrepare(effect.exerciseId))
            is MovitExploreEffect.NavigateToItem -> {
                when (effect.type) {
                    ExploreItemType.Exercise -> pushInner(MovitInnerRoute.ExercisePrepare(effect.id))
                    ExploreItemType.Workout -> pushInner(MovitInnerRoute.WorkoutSession(effect.id))
                    ExploreItemType.Program -> pushInner(
                        MovitInnerRoute.ProgramWeekPlan(programId = effect.id, weekNumber = 1),
                    )
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

    private fun popAllInner() {
        _state.update { it.copy(innerStack = emptyList()) }
    }

    private fun navigateTo(destination: MovitAppDestination) {
        _state.update { it.copy(selectedDestination = destination) }
    }

    companion object {
        internal fun resolveStartupInnerStack(
            bootstrap: AuthBootstrapContext,
            onboardingCompleted: Boolean,
        ): List<MovitInnerRoute> {
            return when (MovitAuthViewModel.resolveBootstrapTarget(bootstrap)) {
                AuthBootstrapTarget.ActiveSession -> {
                    if (onboardingCompleted) {
                        emptyList()
                    } else {
                        listOf(MovitInnerRoute.ProfileOnboarding)
                    }
                }
                AuthBootstrapTarget.SignIn,
                AuthBootstrapTarget.SplashThenIntro,
                -> listOf(MovitInnerRoute.Auth)
            }
        }
    }
}
