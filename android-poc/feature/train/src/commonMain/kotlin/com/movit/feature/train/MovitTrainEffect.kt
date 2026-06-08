package com.movit.feature.train

sealed interface MovitTrainEffect {
    data object OpenSessionPreview : MovitTrainEffect
    data object OpenExplore : MovitTrainEffect
    data object OpenReports : MovitTrainEffect
    data class ShowMessage(val message: String) : MovitTrainEffect
}
