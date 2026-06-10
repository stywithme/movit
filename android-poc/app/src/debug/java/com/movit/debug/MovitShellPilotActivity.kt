package com.movit.debug

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.movit.feature.shell.MovitAppShellRoute
import com.movit.legacy.LegacyTrainingLauncher
import com.trainingvalidator.poc.ui.subscription.SubscriptionActivity

/**
 * Debug-only root host for the Movit App Shell (Phase 03).
 * Not the production launcher — open via adb for visual QA.
 */
class MovitShellPilotActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MovitDataInstall.install(applicationContext)
        enableEdgeToEdge()
        setContent {
            MovitAppShellRoute(
                onLaunchLegacyTraining = { effect ->
                    LegacyTrainingLauncher.startCameraExercise(
                        context = this@MovitShellPilotActivity,
                        exerciseFileName = effect.exerciseFileName,
                        poseVariant = effect.poseVariant,
                    )
                    true
                },
                onLaunchLegacySubscription = {
                    startActivity(Intent(this@MovitShellPilotActivity, SubscriptionActivity::class.java))
                    true
                },
            )
        }
    }
}
