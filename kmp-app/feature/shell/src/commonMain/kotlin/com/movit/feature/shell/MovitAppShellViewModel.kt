package com.movit.feature.shell

import androidx.lifecycle.ViewModel
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
import com.movit.feature.library.RequestedStart
import com.movit.feature.library.WorkoutLaunchCoordinator
import com.movit.feature.library.WorkoutLaunchRequest
import com.movit.feature.library.WorkoutRunStore
import com.movit.feature.library.WorkoutSessionKeys
import com.movit.feature.train.MovitTrainEffect
import com.movit.feature.train.TrainReportTargetUi
import com.movit.feature.train.TrainWorkoutLaunchUi
import com.movit.resources.strings.SessionStrings
import com.movit.shared.AppResult
import com.movit.shared.PlatformInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MovitAppShellViewModel : ViewModel() {
    // ponytail: host tests have no Main dispatcher (same as WorkoutSession preflight).
    // Ceiling: backgroundScope outlives VM if not cancelled; upgrade: test Main rule or onCleared cancel.
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(MovitAppShellState())
    val state: StateFlow<MovitAppShellState> = _state.asStateFlow()

    private val _syncOutcome = MutableStateFlow<MovitSyncOrchestrator.SyncOutcome?>(null)
    val syncOutcome: StateFlow<MovitSyncOrchestrator.SyncOutcome?> = _syncOutcome.asStateFlow()

    private val _cacheInvalidated = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val cacheInvalidated: SharedFlow<Unit> = _cacheInvalidated.asSharedFlow()

    init {
        ShellSyncCoordinator.install { forceCheck -> requestSyncIfNeeded(forceCheck) }
        MovitConnectivitySignals.setOnConnectivityRestored {
            ShellSyncCoordinator.requestSync(forceCheck = true)
        }
        if (MovitData.isInstalled) {
            backgroundScope.launch {
                MovitData.sessionExpiredEvents.collect {
                    popAllInner()
                    pushInner(MovitInnerRoute.Auth)
                }
            }
            // Keep legacy callback as a no-op fallback for hosts that still assign it.
            MovitData.onSessionExpired = null
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
                // P2.11: await bootstrap before first sync (cold-start ordering).
                backgroundScope.launch {
                    MovitData.bootstrapLocalCaches()
                    maybeShowGuestOutboxPrompt()
                    requestSyncIfNeeded()
                }
                ensureOnboardingGateIfNeeded()
            }
            backgroundScope.launch {
                MovitData.sync.cacheInvalidated.collect {
                    notifyCacheInvalidated()
                }
            }
        }
    }

    fun onAppResumed() {
        requestSyncIfNeeded()
    }

    fun onConnectivityRestored() {
        requestSyncIfNeeded(forceCheck = true)
    }

    fun notifyCacheInvalidated() {
        _state.update { it.copy(dataRevision = it.dataRevision + 1) }
        _cacheInvalidated.tryEmit(Unit)
    }

    private fun ensureOnboardingGateIfNeeded() {
        backgroundScope.launch {
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

        backgroundScope.launch {
            val outcome = MovitData.sync.syncIfNeeded(forceCheck = forceCheck)
            _syncOutcome.value = outcome
            // dataRevision / cacheInvalidated come from sync.cacheInvalidated (P2.10).
        }
    }

    override fun onCleared() {
        MovitConnectivitySignals.setOnConnectivityRestored(null)
        ShellSyncCoordinator.clear()
        backgroundScope.cancel()
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
                if (event.destination == MovitAppDestination.Profile) {
                    pushInner(MovitInnerRoute.Profile)
                } else {
                    _state.update { it.copy(selectedDestination = event.destination) }
                }
            }
            is MovitAppShellEvent.InnerRoutePushed -> pushInner(event.route)
            MovitAppShellEvent.InnerRoutePopped -> popInner()
            is MovitAppShellEvent.ReplaceWorkoutJourneyWithReport -> replaceWorkoutJourneyWithReport(
                reportId = event.reportId,
                returnTarget = event.returnTarget,
                doneTarget = event.doneTarget,
            )
            MovitAppShellEvent.ExitWorkoutJourney -> exitWorkoutJourney()
            is MovitAppShellEvent.NavigateReportReturn -> navigateReportReturn(
                target = event.target,
                clearInner = event.clearInner,
            )
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
            MovitAppShellEvent.TabProfileClicked -> pushInner(MovitInnerRoute.Profile)
            is MovitAppShellEvent.ProfileEffectReceived -> handleProfileEffect(event.effect)
            is MovitAppShellEvent.AuthEffectReceived -> handleAuthEffect(event.effect)
            is MovitAppShellEvent.OnboardingEffectReceived -> handleOnboardingEffect(event.effect)
            is MovitAppShellEvent.AssessmentEffectReceived -> handleAssessmentEffect(event.effect)
            is MovitAppShellEvent.LevelEffectReceived -> handleLevelEffect(event.effect)
            MovitAppShellEvent.GuestOutboxAcceptClicked -> resolveGuestOutbox(accept = true)
            MovitAppShellEvent.GuestOutboxDiscardClicked -> resolveGuestOutbox(accept = false)
        }
    }

    private fun handleHomeEffect(effect: MovitHomeEffect) {
        when (effect) {
            MovitHomeEffect.OpenTrain -> navigateTo(MovitAppDestination.Train)
            MovitHomeEffect.OpenExplore -> navigateTo(MovitAppDestination.Explore)
            MovitHomeEffect.OpenReports -> navigateTo(MovitAppDestination.Reports)
            MovitHomeEffect.OpenProfile -> pushInner(MovitInnerRoute.Profile)
            MovitHomeEffect.OpenNotifications -> {
                _effects.tryEmit(MovitAppShellEffect.ShowLocalizedMessage("home_notifications_coming_soon"))
            }
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
                    openWorkoutLaunch(
                        WorkoutLaunchCoordinator.fromTrainProgramDay(
                            programId = effect.programId,
                            weekNumber = effect.weekNumber,
                            dayNumber = effect.dayNumber,
                            plannedWorkoutId = WorkoutSessionKeys.AUTO_PLANNED_WORKOUT,
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
            is MovitProfileEffect.OpenSubscription -> {
                if (PlatformInfo.supportsInAppSubscription) {
                    _effects.tryEmit(
                        MovitAppShellEffect.LaunchPlatformSubscription(
                            restorePurchases = effect.restorePurchases,
                        ),
                    )
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
            MovitProfileEffect.OpenTrainingDebugLab -> {
                if (PlatformInfo.supportsTrainingDebugLab) {
                    pushInner(MovitInnerRoute.TrainingDebugLab())
                }
            }
        }
    }

    private fun handleAuthEffect(effect: MovitAuthEffect) {
        when (effect) {
            MovitAuthEffect.OpenShell -> {
                popInner()
                backgroundScope.launch {
                    maybeShowGuestOutboxPrompt()
                    requestSyncIfNeeded(forceCheck = true)
                }
            }
            MovitAuthEffect.OpenOnboarding -> {
                popInner()
                pushInner(MovitInnerRoute.ProfileOnboarding)
                backgroundScope.launch {
                    maybeShowGuestOutboxPrompt()
                    requestSyncIfNeeded(forceCheck = true)
                }
            }
            is MovitAuthEffect.ShowMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowMessage(effect.message))
            }
            is MovitAuthEffect.ShowLocalizedMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowLocalizedMessage(effect.key))
            }
        }
    }

    /** UX.7 safety net — cold start or OpenShell if auth UI did not already resolve the prompt. */
    private suspend fun maybeShowGuestOutboxPrompt() {
        if (!MovitData.isInstalled) return
        if (_state.value.guestOutboxPromptCount != null) return
        val prompt = MovitData.pendingGuestOutboxPrompt() ?: return
        val userId = MovitData.requirePlatform().userId()?.takeIf { it.isNotBlank() } ?: return
        _state.update {
            it.copy(
                guestOutboxPromptCount = prompt.guestRowCount,
                guestOutboxUserId = userId,
            )
        }
    }

    private fun resolveGuestOutbox(accept: Boolean) {
        val userId = _state.value.guestOutboxUserId
        backgroundScope.launch {
            if (MovitData.isInstalled && userId != null) {
                if (accept) {
                    MovitData.acceptGuestOutboxAttribution(userId)
                } else {
                    MovitData.discardGuestOutboxAttribution()
                }
            }
            _state.update {
                it.copy(guestOutboxPromptCount = null, guestOutboxUserId = null)
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
                    _effects.tryEmit(MovitAppShellEffect.LaunchPlatformSubscription(restorePurchases = false))
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
            is MovitTrainEffect.OpenProgramWeek -> pushInner(
                MovitInnerRoute.ProgramDetail(
                    programId = effect.programId,
                    initialWeekNumber = effect.weekNumber,
                ),
            )
            is MovitTrainEffect.OpenWeeklyReport -> pushInner(
                MovitInnerRoute.WeeklyReport(
                    programId = effect.programId,
                    weekNumber = effect.weekNumber,
                ),
            )
            is MovitTrainEffect.OpenReport -> openTrainReport(effect.target)
            MovitTrainEffect.OpenExplore -> navigateTo(MovitAppDestination.Explore)
            MovitTrainEffect.OpenReports -> navigateTo(MovitAppDestination.Reports)
            is MovitTrainEffect.OpenProgramWorkout -> openTrainWorkoutSession(effect.target)
            is MovitTrainEffect.ShowMessage -> {
                _effects.tryEmit(MovitAppShellEffect.ShowMessage(effect.message))
            }
        }
    }

    private fun openTrainReport(target: TrainReportTargetUi) {
        when (target) {
            is TrainReportTargetUi.ProgramWeek -> pushInner(
                MovitInnerRoute.WeeklyReport(
                    programId = target.programId,
                    weekNumber = target.weekNumber,
                ),
            )
            is TrainReportTargetUi.ProgramDay -> {
                val reportId = target.reportId
                if (!reportId.isNullOrBlank()) {
                    pushInner(MovitInnerRoute.ReportDetail(reportId))
                } else {
                    // Legacy/defensive — mapper should emit ProgramWeek when id is absent.
                    pushInner(
                        MovitInnerRoute.WeeklyReport(
                            programId = target.programId,
                            weekNumber = target.weekNumber,
                        ),
                    )
                }
            }
            is TrainReportTargetUi.WorkoutRun -> pushInner(MovitInnerRoute.ReportDetail(target.reportId))
        }
    }

    private fun openTrainWorkoutSession(target: TrainWorkoutLaunchUi) {
        openWorkoutLaunch(
            request = WorkoutLaunchCoordinator.fromTrainProgramDay(
                programId = target.programId,
                weekNumber = target.weekNumber,
                dayNumber = target.dayNumber,
                plannedWorkoutId = target.plannedWorkoutId,
            ),
            prepareTrainTarget = target,
        )
    }

    /**
     * Single WorkoutSession entry for Explore / Train / catch-up.
     * Resolves [WorkoutLaunchRequest] → session id, opens immediately; optional Train sync in background.
     */
    private fun openWorkoutLaunch(
        request: WorkoutLaunchRequest,
        prepareTrainTarget: TrainWorkoutLaunchUi? = null,
    ) {
        val sessionId = WorkoutLaunchCoordinator.remember(request)
        if (request.requestedStart == RequestedStart.BeginFresh) {
            WorkoutRunStore.abandonActiveForWorkout(sessionId)
        }
        // Open immediately — preflight/sync happens inside WorkoutSession (P1.1).
        pushInner(MovitInnerRoute.WorkoutSession(sessionId))
        if (prepareTrainTarget == null || !MovitData.isInstalled) return
        backgroundScope.launch {
            prepareTrainWorkoutSession(prepareTrainTarget)
        }
    }

    private suspend fun prepareTrainWorkoutSession(target: TrainWorkoutLaunchUi): Boolean {
        val cachedUserProgramId = MovitData.plan.readCachedActiveUserProgramId()
        if (!cachedUserProgramId.isNullOrBlank() && syncTrainWorkoutPlan(cachedUserProgramId, target)) {
            return true
        }

        val refreshedUserProgramId = when (val refreshed = MovitData.plan.refreshActiveUserProgramId(target.programId)) {
            is AppResult.Success -> refreshed.value
            is AppResult.Failure -> null
        } ?: return false

        return syncTrainWorkoutPlan(refreshedUserProgramId, target)
    }

    private suspend fun syncTrainWorkoutPlan(
        userProgramId: String,
        target: TrainWorkoutLaunchUi,
    ): Boolean =
        when (
            MovitData.workoutSession.syncEffectivePlan(
                userProgramId = userProgramId,
                weekNumber = target.weekNumber,
                dayNumber = target.dayNumber,
            )
        ) {
            is AppResult.Success -> true
            is AppResult.Failure -> false
        }

    private fun handleExploreEffect(effect: MovitExploreEffect) {
        when (effect) {
            MovitExploreEffect.OpenExercisesLibrary -> pushInner(MovitInnerRoute.ExercisesLibrary)
            MovitExploreEffect.OpenWorkoutsLibrary -> pushInner(MovitInnerRoute.WorkoutsLibrary)
            MovitExploreEffect.OpenProgramList -> pushInner(MovitInnerRoute.ProgramList)
            is MovitExploreEffect.OpenProgramDetail -> pushInner(
                MovitInnerRoute.ProgramDetail(programId = effect.programId),
            )
            is MovitExploreEffect.OpenWorkoutSession ->
                openWorkoutLaunch(WorkoutLaunchCoordinator.fromExploreWorkout(effect.workoutId))
            is MovitExploreEffect.OpenExercisePrepare -> pushInner(
                MovitInnerRoute.ExercisePrepare(exerciseId = effect.exerciseId),
            )
            is MovitExploreEffect.NavigateToItem -> {
                when (effect.type) {
                    ExploreItemType.Exercise -> pushInner(MovitInnerRoute.ExercisePrepare(effect.id))
                    ExploreItemType.Workout ->
                        openWorkoutLaunch(WorkoutLaunchCoordinator.fromExploreWorkout(effect.id))
                    ExploreItemType.Program -> pushInner(
                        MovitInnerRoute.ProgramDetail(programId = effect.id),
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

    /**
     * Removes ExercisePrepare + TrainingSession for the completed run and places ReportDetail
     * as the terminal route (no report loop on Back).
     */
    private fun replaceWorkoutJourneyWithReport(
        reportId: String,
        returnTarget: com.movit.feature.library.ReturnTarget?,
        doneTarget: com.movit.feature.library.ReturnTarget?,
    ) {
        _state.update { state ->
            val trimmed = state.innerStack.dropLastWhile { route ->
                route is MovitInnerRoute.TrainingSession || route is MovitInnerRoute.ExercisePrepare
            }
            state.copy(
                innerStack = trimmed + MovitInnerRoute.ReportDetail(
                    reportId = reportId,
                    returnTarget = returnTarget,
                    doneTarget = doneTarget,
                ),
            )
        }
    }

    /** Save and exit / End workout — pop Prepare+Training onto WorkoutSession (or empty). */
    private fun exitWorkoutJourney() {
        _state.update { state ->
            state.copy(
                innerStack = state.innerStack.dropLastWhile { route ->
                    route is MovitInnerRoute.TrainingSession || route is MovitInnerRoute.ExercisePrepare
                },
            )
        }
    }

    private fun navigateReportReturn(
        target: com.movit.feature.library.ReturnTarget,
        clearInner: Boolean,
    ) {
        if (clearInner) popAllInner()
        when (target) {
            com.movit.feature.library.ReturnTarget.Train -> navigateTo(MovitAppDestination.Train)
            com.movit.feature.library.ReturnTarget.Explore -> navigateTo(MovitAppDestination.Explore)
            is com.movit.feature.library.ReturnTarget.WorkoutSession -> {
                if (clearInner) {
                    pushInner(MovitInnerRoute.WorkoutSession(target.workoutId))
                }
            }
            is com.movit.feature.library.ReturnTarget.ProgramDetail -> {
                if (clearInner) {
                    pushInner(
                        MovitInnerRoute.ProgramDetail(
                            programId = target.programId,
                            initialWeekNumber = target.weekNumber,
                        ),
                    )
                }
            }
        }
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
