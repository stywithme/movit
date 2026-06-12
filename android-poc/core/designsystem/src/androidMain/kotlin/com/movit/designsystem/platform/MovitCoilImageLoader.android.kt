package com.movit.designsystem.platform

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.crossfade

/**
 * F12 — installs Coil 3 singleton with a bounded disk cache for KMP Compose images.
 */
fun installMovitCoilImageLoader(context: Context) {
    val appContext = context.applicationContext
    SingletonImageLoader.setSafe { ctx ->
        ImageLoader.Builder(ctx)
            .crossfade(true)
            .diskCache {
                DiskCache.Builder()
                    .directory(appContext.cacheDir.resolve(MovitImageCachePolicy.DISK_DIRECTORY_NAME))
                    .maxSizeBytes(MovitImageCachePolicy.DISK_MAX_BYTES)
                    .build()
            }
            .build()
    }
}
