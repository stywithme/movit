package com.movit.feature.trainingdebug

object TrainingDebugReducers {
    fun reduceConfig(current: TrainingDebugConfig, action: TrainingDebugAction): TrainingDebugConfig =
        when (action) {
            is TrainingDebugAction.SetInputMode -> current.copy(inputMode = action.mode)
            is TrainingDebugAction.SetTab -> current.copy(activeTab = action.tab)
            is TrainingDebugAction.ToggleJoint -> {
                val updated = current.selectedJoints.toMutableSet()
                if (action.jointCode in updated) updated.remove(action.jointCode) else updated.add(action.jointCode)
                if (updated.isEmpty()) updated.add("left_knee")
                current.copy(selectedJoints = updated)
            }
            is TrainingDebugAction.SetPositionCheck -> current.copy(positionCheck = action.config)
            is TrainingDebugAction.SetSceneExpectation -> current.copy(sceneExpectation = action.config)
            is TrainingDebugAction.SetModelType -> current.copy(modelType = action.modelType)
            is TrainingDebugAction.SetTiltCorrection -> current.copy(tiltCorrectionEnabled = action.enabled)
            is TrainingDebugAction.SetInfoPanelVisible -> current.copy(infoPanelVisible = action.visible)
        }

    fun shouldResetAnalysis(action: TrainingDebugAction): Boolean = when (action) {
        is TrainingDebugAction.SetInputMode,
        is TrainingDebugAction.SetTab,
        is TrainingDebugAction.SetModelType,
        is TrainingDebugAction.SetTiltCorrection,
        is TrainingDebugAction.SetPositionCheck,
        is TrainingDebugAction.SetSceneExpectation,
        is TrainingDebugAction.ToggleJoint,
        -> true
        else -> false
    }
}

sealed interface TrainingDebugAction {
    data class SetInputMode(val mode: TrainingDebugInputMode) : TrainingDebugAction
    data class SetTab(val tab: TrainingDebugTab) : TrainingDebugAction
    data class ToggleJoint(val jointCode: String) : TrainingDebugAction
    data class SetPositionCheck(val config: DebugPositionCheckConfig) : TrainingDebugAction
    data class SetSceneExpectation(val config: DebugSceneExpectationConfig) : TrainingDebugAction
    data class SetModelType(val modelType: DebugPoseModelType) : TrainingDebugAction
    data class SetTiltCorrection(val enabled: Boolean) : TrainingDebugAction
    data class SetInfoPanelVisible(val visible: Boolean) : TrainingDebugAction
}
