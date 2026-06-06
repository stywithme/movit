package com.trainingvalidator.poc

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.sensors.DeviceTiltProvider
import com.trainingvalidator.poc.storage.SystemMessageStore
import com.trainingvalidator.poc.training.engine.PostureMlpClassifier
import com.trainingvalidator.poc.ui.main.MainContainerActivity
import com.trainingvalidator.poc.ui.theme.AppThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * PoseApp — Application class
 *
 * Provides [applicationScope]: a CoroutineScope tied to the process lifetime,
 * not to any individual Activity. Use it for fire-and-forget background operations
 * (e.g. uploading workout execution data) that must survive Activity destruction.
 */
class PoseApp : Application(), ImageLoaderFactory {

    /**
     * Process-wide coroutine scope.
     * Never cancelled except when the process terminates.
     * Uses [SupervisorJob] so one failed child does not cancel siblings.
     */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tiltProvider: DeviceTiltProvider by lazy {
        DeviceTiltProvider(applicationContext)
    }

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
        AppThemeManager.applySavedMode(this)
        ApiClient.init(this)
        PostureMlpClassifier.getOrNull(this)
        SystemMessageStore(this).loadIntoRegistry()
        registerImmersiveMode()
    }

    /**
     * Applies immersive fullscreen to every Activity except [MainContainerActivity]
     * (which has its own BottomNavigationView and needs visible system bars).
     * System bars auto-hide and reappear transiently on swipe.
     */
    private fun registerImmersiveMode() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is MainContainerActivity) return
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
