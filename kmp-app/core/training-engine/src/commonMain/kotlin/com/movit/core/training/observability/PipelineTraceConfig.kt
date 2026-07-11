package com.movit.core.training.observability

import kotlin.concurrent.Volatile

/** Global debug flag for [PipelineTrace] (I-10). UI agents toggle at session start. */
object PipelineTraceConfig {
    @Volatile
    var isEnabled: Boolean = false
        private set

    @Volatile
    private var trace: PipelineTrace? = null

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        trace = if (enabled) PipelineTrace() else null
    }

    internal fun current(): PipelineTrace? = if (isEnabled) trace else null
}
