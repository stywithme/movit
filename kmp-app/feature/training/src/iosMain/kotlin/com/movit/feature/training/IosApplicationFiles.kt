package com.movit.feature.training

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
internal object IosApplicationFiles {
    fun documentsRoot(): String {
        val paths = NSSearchPathForDirectoriesInDomains(
            platform.Foundation.NSDocumentDirectory,
            NSUserDomainMask,
            true,
        )
        return paths.firstOrNull() as? String ?: "/tmp/movit"
    }
}
