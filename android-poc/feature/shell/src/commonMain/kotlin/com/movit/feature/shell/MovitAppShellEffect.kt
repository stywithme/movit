package com.movit.feature.shell

sealed interface MovitAppShellEffect {
    data class ShowMessage(val message: String) : MovitAppShellEffect
}
