package com.movit.designsystem.platform

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.crossfade
import kotlinx.cinterop.ExperimentalForeignApi
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * F12 — installs Coil 3 singleton with a bounded disk cache for KMP Compose images (iOS).
 */
@OptIn(ExperimentalForeignApi::class)
fun installMovitCoilImageLoader() {
    val cacheRoot = NSFileManager.defaultManager.URLForDirectory(
        directory = NSCachesDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    )!!.path!!
    val diskDir = "$cacheRoot/${MovitImageCachePolicy.DISK_DIRECTORY_NAME}"
    SingletonImageLoader.setSafe {
        ImageLoader.Builder(it)
            .crossfade(true)
            .diskCache {
                DiskCache.Builder()
                    .directory(diskDir.toPath())
                    .maxSizeBytes(MovitImageCachePolicy.DISK_MAX_BYTES)
                    .build()
            }
            .build()
    }
}
