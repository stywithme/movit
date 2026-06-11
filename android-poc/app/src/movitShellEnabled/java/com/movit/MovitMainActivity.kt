package com.movit

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.movit.host.attachMovitShellHost
import com.trainingvalidator.poc.BuildConfig

/**
 * Post-login home for the Movit KMP shell (Phase 06 G-3 — Strategy B).
 *
 * [com.trainingvalidator.poc.ui.auth.SplashActivity] remains LAUNCHER; legacy auth
 * routes here when [com.trainingvalidator.poc.BuildConfig.MOVIT_SHELL_LAUNCHER_ENABLED] is true.
 */
class MovitMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        attachMovitShellHost(
            exitToLegacyAuthOnLogout = true,
            trainingKmpEnabled = BuildConfig.MOVIT_TRAINING_KMP_ENABLED,
            launchIntent = intent,
        )
    }
}
