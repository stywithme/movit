package com.movit.feature.shell

data class MovitAppShellState(
    val selectedDestination: MovitAppDestination = MovitAppDestination.Home,
    val headerUserName: String = "Athlete",
    val innerStack: List<MovitInnerRoute> = emptyList(),
) {
    val currentInnerRoute: MovitInnerRoute?
        get() = innerStack.lastOrNull()
}
