package com.movit.feature.shell

import com.movit.core.data.platform.MovitThemeModeStorage

data class MovitAppShellState(
    val selectedDestination: MovitAppDestination = MovitAppDestination.Home,
    val headerUserName: String = "Athlete",
    val innerStack: List<MovitInnerRoute> = emptyList(),
    val themeMode: String = MovitThemeModeStorage.SYSTEM,
    val localeRevision: Int = 0,
) {
    val currentInnerRoute: MovitInnerRoute?
        get() = innerStack.lastOrNull()
}
