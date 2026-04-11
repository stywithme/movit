package com.trainingvalidator.poc

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.trainingvalidator.poc.storage.SystemMessageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * PoseApp — Application class
 *
 * Provides [applicationScope]: a CoroutineScope tied to the process lifetime,
 * not to any individual Activity. Use it for fire-and-forget background operations
 * (e.g. uploading session data) that must survive Activity destruction.
 */
class PoseApp : Application(), ImageLoaderFactory {

    /**
     * Process-wide coroutine scope.
     * Never cancelled except when the process terminates.
     * Uses [SupervisorJob] so one failed child does not cancel siblings.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private lateinit var _instance: PoseApp

        /** Safe accessor — always valid after [onCreate] */
        val instance: PoseApp get() = _instance
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this
        SystemMessageStore(this).loadIntoRegistry()
    }
}
