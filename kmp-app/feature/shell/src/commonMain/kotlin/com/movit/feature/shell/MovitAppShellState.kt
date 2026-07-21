package com.movit.feature.shell

import com.movit.core.data.platform.MovitThemeModeStorage

data class MovitAppShellState(
    val selectedDestination: MovitAppDestination = MovitAppDestination.Home,
    val headerUserName: String = "Athlete",
    val innerStack: List<MovitInnerRoute> = emptyList(),
    val themeMode: String = MovitThemeModeStorage.SYSTEM,
    val localeRevision: Int = 0,
    val dataRevision: Int = 0,
    val bootstrap: DataBootstrapUiState = DataBootstrapUiState.Hidden,
    val showSyncStatusSheet: Boolean = false,
    /** UX.7 — guest outbox attribution dialog (cold start / OpenShell safety net). */
    val guestOutboxPromptCount: Int? = null,
    val guestOutboxUserId: String? = null,
) {
    val currentInnerRoute: MovitInnerRoute?
        get() = innerStack.lastOrNull()

    val blocksMainTabs: Boolean
        get() = bootstrap is DataBootstrapUiState.Loading || bootstrap is DataBootstrapUiState.Failed
}
