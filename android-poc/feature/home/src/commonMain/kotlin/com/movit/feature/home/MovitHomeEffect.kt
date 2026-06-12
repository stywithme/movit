package com.movit.feature.home

sealed interface MovitHomeEffect {
    data object OpenTrain : MovitHomeEffect
    data object OpenExplore : MovitHomeEffect
    data object OpenReports : MovitHomeEffect
    data object OpenProfile : MovitHomeEffect
    data object OpenAssessment : MovitHomeEffect
    data object OpenLevel : MovitHomeEffect
    data class OpenReportDetail(val reportId: String) : MovitHomeEffect
    data class OpenProgramDetail(val programId: String) : MovitHomeEffect
    data class OpenCatchUpDay(
        val programId: String,
        val weekNumber: Int,
        val dayNumber: Int,
    ) : MovitHomeEffect
    data class ShowMessage(val message: String) : MovitHomeEffect
}
