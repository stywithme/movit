package com.movit.feature.home

sealed interface MovitHomeEffect {
    data object OpenTrain : MovitHomeEffect
    data object OpenExplore : MovitHomeEffect
    data object OpenReports : MovitHomeEffect
    data object OpenProfile : MovitHomeEffect
    data class ShowMessage(val message: String) : MovitHomeEffect
}
