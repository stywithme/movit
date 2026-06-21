package com.movit.core.training.observability

import kotlin.concurrent.Volatile

/** * Global debug flag for [PipelineTrace] (I-10). UI agents toggle at session start.
 */
object PipelineTraceConfig {
    @Volatile
    var isEnabled: Boolean = false
        private set

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
}
