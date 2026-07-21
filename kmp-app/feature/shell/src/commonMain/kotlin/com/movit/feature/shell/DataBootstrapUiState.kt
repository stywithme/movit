package com.movit.feature.shell

sealed interface DataBootstrapUiState {
  data object Hidden : DataBootstrapUiState

  data class Loading(
      val stageKey: String,
  ) : DataBootstrapUiState

  data class Failed(
      val errorKey: String,
      val allowPartialContinue: Boolean,
  ) : DataBootstrapUiState
}
