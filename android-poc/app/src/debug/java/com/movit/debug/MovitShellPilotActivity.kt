package com.movit.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.movit.host.attachMovitShellHost
import com.trainingvalidator.poc.BuildConfig

/**
 * Debug-only entry for Movit shell visual QA (adb / debug launcher).
 * Delegates to the same unified host as [com.movit.MovitMainActivity].
 */
class MovitShellPilotActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        attachMovitShellHost(
            exitToLegacyAuthOnLogout = false,
            trainingKmpEnabled = BuildConfig.MOVIT_TRAINING_KMP_ENABLED,
            launchIntent = intent,
        )
    }
}
