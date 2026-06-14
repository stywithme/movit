package com.movit

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.movit.host.attachMovitShellHost
import com.trainingvalidator.poc.BuildConfig

/**
 * Production LAUNCHER for the Movit KMP shell (Phase A closure).
 *
 * Auth bootstrap is in-shell ([MovitInnerRoute.Auth]); logout stays in-shell when
 * [exitToLegacyAuthOnLogout] is false. Legacy [SplashActivity] remains for rollback paths.
 */
class MovitMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        attachMovitShellHost(
            exitToLegacyAuthOnLogout = false,
            trainingKmpEnabled = BuildConfig.MOVIT_TRAINING_KMP_ENABLED,
            launchIntent = intent,
        )
    }
}
