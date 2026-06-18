package com.movit.feature.shell

import com.movit.core.data.MovitData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val legacyDrainScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

internal actual fun drainLegacyWorkoutExecutionsOnResume() {
    if (!MovitData.isInstalled) return
    legacyDrainScope.launch {
        runCatching { MovitData.drainLegacyWorkoutExecutions() }
    }
}
