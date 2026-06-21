package com.movit.feature.library

sealed interface ProgramListEffect {
    data class OpenProgram(val programId: String) : ProgramListEffect
}
