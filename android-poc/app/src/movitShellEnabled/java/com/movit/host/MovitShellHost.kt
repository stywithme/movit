package com.movit.host

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.movit.feature.shell.MovitAppShellHost
import com.movit.legacy.LegacyTrainingLauncher
import com.trainingvalidator.poc.ui.auth.SplashActivity
import com.trainingvalidator.poc.ui.subscription.SubscriptionActivity

fun ComponentActivity.attachMovitShellHost(
    exitToLegacyAuthOnLogout: Boolean,
) {
    MovitDataInstall.install(applicationContext)
    enableEdgeToEdge()

    setContent {
        MovitAppShellHost(
            legacyAuthExitEnabled = exitToLegacyAuthOnLogout,
            onHostBackPressed = { finish() },
            onLaunchLegacyTraining = { effect ->
                LegacyTrainingLauncher.startCameraExercise(
                    context = this@attachMovitShellHost,
                    exerciseFileName = effect.exerciseFileName,
                    poseVariant = effect.poseVariant,
                )
                true
            },
            onLaunchLegacySubscription = {
                startActivity(Intent(this@attachMovitShellHost, SubscriptionActivity::class.java))
                true
            },
            onNavigateToLegacyAuth = {
                navigateToLegacyAuth()
                true
            },
        )
    }
}

private fun ComponentActivity.navigateToLegacyAuth() {
    startActivity(
        Intent(this, SplashActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        },
    )
    finish()
}
