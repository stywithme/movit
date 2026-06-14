package com.trainingvalidator.poc

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.movit.billing.installBillingHost
import com.movit.core.data.local.MovitAndroidRuntime
import com.movit.core.data.sync.BackgroundSyncScheduler
import com.movit.core.posecapture.android.PoseLandmarkerHeavyModelStore
import com.movit.MovitMainActivity
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.training.config.SettingsManager
import com.trainingvalidator.poc.ui.theme.AppThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * PoseApp — Application class
 *
 * Provides [applicationScope]: a CoroutineScope tied to the process lifetime,
 * not to any individual Activity. Use it for fire-and-forget background operations
 * that must survive Activity destruction.
 */
class PoseApp : Application() {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private lateinit var _instance: PoseApp

        val instance: PoseApp get() = _instance
    }

    override fun onCreate() {
        super.onCreate()
        _instance = this
        MovitAndroidRuntime.applicationContext = applicationContext
        BackgroundSyncScheduler.schedule()
        AppThemeManager.applySavedMode(this)
        ApiClient.init(this)
        installBillingHost(this)
        preloadHeavyPoseModelIfNeeded()
        registerImmersiveMode()
    }

    private fun preloadHeavyPoseModelIfNeeded() {
        SettingsManager.initialize(this)
        if (SettingsManager.getModelType() != "heavy") return
        applicationScope.launch {
            PoseLandmarkerHeavyModelStore.ensureCached(applicationContext)
        }
    }

    private fun registerImmersiveMode() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                if (activity is MovitMainActivity) return
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
