package com.movit.feature.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.movit.core.data.MovitData
import com.movit.core.data.platform.MovitConnectivitySignals
import com.movit.core.data.sync.MovitSyncOrchestrator
import com.movit.feature.account.AuthBootstrapContext
import com.movit.feature.account.AuthBootstrapTarget
import com.movit.feature.account.MovitAssessmentEffect
import com.movit.feature.account.MovitAuthEffect
import com.movit.feature.account.MovitAuthViewModel
import com.movit.feature.account.MovitLevelEffect
import com.movit.feature.account.MovitOnboardingEffect
import com.movit.feature.account.MovitProfileEffect
import com.movit.feature.account.defaultOnboardingRepository
import com.movit.core.model.ExploreItemType
import com.movit.feature.explore.MovitExploreEffect
import com.movit.feature.home.MovitHomeEffect
import com.movit.feature.reports.MovitReportsEffect
import com.movit.feature.library.WorkoutSessionKeys
import com.movit.feature.train.MovitTrainEffect
import com.movit.shared.PlatformInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MovitAppShellViewModel(
    private val legacyAuthExitEnabled: Boolean = false,
) : ViewModel() {
    private val _state = MutableStateFlow(MovitAppShellState())
    val state: StateFlow<MovitAppShellState> = _state.asStateFlow()

    private val _syncOutcome = MutableStateFlow<MovitSyncOrchestrator.SyncOutcome?>(null)
    val syncOutcome: StateFlow<MovitSyncOrchestrator.SyncOutcome?> = _syncOutcome.asStateFlow()

    init {
        ShellSyncCoordinator.install { forceCheck -> requestSyncIfNeeded(forceCheck) }
        MovitConnectivitySignals.setOnConnectivityRestored {
            ShellSyncCoordinator.requestSync(forceCheck = true)
        }
        if (MovitData.isInstalled) {
            viewModelScope.launch {
                MovitData.bootstrapLocalCaches()
            }
            MovitData.onSessionExpired = {
                if (legacyAuthExitEnabled) {
                    _effects.tryEmit(MovitAppShellEffect.NavigateToLegacyAuth)
                } else {
                    popAllInner()
                    pushInner(MovitInnerRoute.Auth)
                }
            }
            val platform = MovitData.requirePlatform()
            val bootstrap = AuthBootstrapContext.fromMovitData()
            val startupStack = resolveStartupInnerStack(bootstrap = bootstrap)
            val deepLinkStack = MovitShellPendingNavigation.consume()
            _state.update {
                it.copy(
                    themeMode = platform.themeMode(),
                    innerStack = startupStack + deepLinkStack,
                )
            }
            if (bootstrap.hasActiveSession) {
                requestSyncIfNeeded()
                ensureOnboardingGateIfNeeded()
            }
        }
    }

    fun onAppResumed() {
        requestSyncIfNeeded()
    }

    fun onConnectivityRestored() {
        requestSyncIfNeeded(forceCheck = true)
    }

    private fun ensureOnboardingGateIfNeeded() {
        viewModelScope.launch {
            if (!MovitData.isInstalled) return@launch
            if (!AuthBootstrapContext.fromMovitData().hasActiveSession) return@launch
            val needsOnboarding = defaultOnboardingRepository().resolveNeedsOnboarding()
            if (needsOnboarding && _state.value.innerStack.none { it == MovitInnerRoute.ProfileOnboarding }) {
                pushInner(MovitInnerRoute.ProfileOnboarding)
            }
        }
    }

    private fun requestSyncIfNeeded(forceCheck: Boolean = false) {
        if (!MovitData.isInstalled) return
        if (!AuthBootstrapContext.fromMovitData().hasActiveSession) return

        viewModelScope.launch {
            val outcome = MovitData.sync.syncIfNeeded(forceCheck = forceCheck)
            _syncOutcome.value = outcome
        }
    }

    override fun onCleared() {
        MovitConnectivitySignals.setOnConnectivityRestored(null)
        ShellSyncCoordinator.clear()
        super.onCleared()
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
                pushInner(MovitInnerRoute.ExerciseDetail(event.itemId))
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
            MovitHomeEffect.OpenAssessment -> pushInner(MovitInnerRoute.Assessment())
            MovitHomeEffect.OpenLevel -> pushInner(MovitInnerRoute.LevelProfile)
            is MovitHomeEffect.OpenReportDetail -> pushInner(MovitInnerRoute.ReportDetail(effect.reportId))
            is MovitHomeEffect.OpenProgramDetail -> pushInner(MovitInnerRoute.ProgramDetail(effect.programId))
            is MovitHomeEffect.OpenCatchUpDay -> {
                if (effect.programId.isBlank()) {
                    _effects.tryEmit(
                        MovitAppShellEffect.ShowLocalizedMessage("home_catch_up_unavailable"),
                    )
                } else {
                    pushInner(
                        MovitInnerRoute.WorkoutSession(
                            WorkoutSessionKeys.encode(
                                programId = effect.programId,
                                weekNumber = effect.weekNumber,
                                dayNumber = effect.dayNumber,
                                plannedWorkoutId = WorkoutSessionKeys.AUTO_PLANNED_WORKOUT,
                            ),
                        ),
                    )
                }
            }
            is MovitHomeEffect.ShowMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowMessage(effect.message))
            }
        }
    }

    private fun handleProfileEffect(effect: MovitProfileEffect) {
        when (effect) {
            MovitProfileEffect.OpenAuth -> pushInner(MovitInnerRoute.Auth)
            MovitProfileEffect.OpenOnboarding -> pushInner(MovitInnerRoute.ProfileOnboarding)
            MovitProfileEffect.OpenAssessment -> pushInner(MovitInnerRoute.Assessment())
            MovitProfileEffect.OpenLevel -> pushInner(MovitInnerRoute.LevelProfile)
            MovitProfileEffect.OpenSubscription -> {
                if (PlatformInfo.supportsInAppSubscription) {
                    _effects.tryEmit(MovitAppShellEffect.LaunchLegacySubscription)
                } else {
                    _effects.tryEmit(
                        MovitAppShellEffect.ShowLocalizedMessage("profile_subscription_ios_unavailable"),
                    )
                }
            }
            is MovitProfileEffect.ShowLocalizedMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowLocalizedMessage(effect.key))
            }
            MovitProfileEffect.LoggedOut -> {
                if (legacyAuthExitEnabled) {
                    _effects.tryEmit(MovitAppShellEffect.NavigateToLegacyAuth)
                } else {
                    popAllInner()
                    pushInner(MovitInnerRoute.Auth)
                }
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
            MovitAuthEffect.OpenShell -> {
                popInner()
                requestSyncIfNeeded(forceCheck = true)
            }
            MovitAuthEffect.OpenOnboarding -> {
                popInner()
                pushInner(MovitInnerRoute.ProfileOnboarding)
                requestSyncIfNeeded(forceCheck = true)
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
            MovitOnboardingEffect.Completed -> {
                popInner()
                requestSyncIfNeeded(forceCheck = true)
            }
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
            is MovitLevelEffect.OpenAssessment -> pushInner(MovitInnerRoute.Assessment(mode = effect.mode))
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
                if (PlatformInfo.supportsInAppSubscription) {
                    _effects.tryEmit(MovitAppShellEffect.LaunchLegacySubscription)
                } else {
                    _effects.tryEmit(
                        MovitAppShellEffect.ShowLocalizedMessage("profile_subscription_ios_unavailable"),
                    )
                }
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
            MovitTrainEffect.OpenAssessment -> pushInner(MovitInnerRoute.Assessment())
            is MovitTrainEffect.OpenProgramDetail -> pushInner(MovitInnerRoute.ProgramDetail(effect.programId))
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
                MovitInnerRoute.ProgramDetail(programId = effect.programId),
            )
            is MovitExploreEffect.OpenWorkoutSession -> pushInner(MovitInnerRoute.WorkoutSession(effect.workoutId))
            is MovitExploreEffect.OpenExerciseDetail -> pushInner(
                MovitInnerRoute.ExerciseDetail(exerciseId = effect.exerciseId),
            )
            is MovitExploreEffect.NavigateToItem -> {
                when (effect.type) {
                    ExploreItemType.Exercise -> pushInner(MovitInnerRoute.ExerciseDetail(effect.id))
                    ExploreItemType.Workout -> pushInner(MovitInnerRoute.WorkoutSession(effect.id))
                    ExploreItemType.Program -> pushInner(
                        MovitInnerRoute.ProgramDetail(programId = effect.id),
                    )
                }
            }
            is MovitExploreEffect.NavigateToExercise -> {
                pushInner(MovitInnerRoute.ExerciseDetail(effect.id))
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
        ): List<MovitInnerRoute> {
            return when (MovitAuthViewModel.resolveBootstrapTarget(bootstrap)) {
                AuthBootstrapTarget.ActiveSession -> emptyList()
                AuthBootstrapTarget.SignIn,
                AuthBootstrapTarget.SplashThenIntro,
                -> listOf(MovitInnerRoute.Auth)
            }
        }
    }
}
