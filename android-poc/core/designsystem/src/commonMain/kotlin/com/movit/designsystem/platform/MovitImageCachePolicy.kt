package com.movit.designsystem.platform

/**
 * F12 — documented Coil 3 disk cache ceiling for remote images ([com.movit.designsystem.components.MovitRemoteImage]).
 *
 * Default Coil disk cache is ~250MB on disk; we cap at **64 MiB** to align with audio cache budgeting (100 MiB).
 */
object MovitImageCachePolicy {
    const val DISK_MAX_BYTES: Long = 64L * 1024L * 1024L
    const val DISK_DIRECTORY_NAME: String = "movit_coil3_images"
}
