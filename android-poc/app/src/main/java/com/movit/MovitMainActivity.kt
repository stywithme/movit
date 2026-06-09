package com.movit

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.trainingvalidator.poc.BuildConfig
import com.trainingvalidator.poc.ui.auth.SplashActivity

/**
 * Target production launcher for the Movit KMP shell (Pre-06 WS-C).
 *
 * Disabled by default: forwards to legacy [SplashActivity] until the Launcher Gate
 * flip checklist completes. See Docs/.../Android-KMP-Mobile-UI-UX-Launcher-Gate.md
 *
 * When [BuildConfig.MOVIT_SHELL_LAUNCHER_ENABLED] is true and release classpath
 * includes Movit modules, replace this body with [com.movit.debug.MovitShellPilotActivity]
 * host logic and swap the MAIN/LAUNCHER intent-filter in the manifest.
 */
class MovitMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!BuildConfig.MOVIT_SHELL_LAUNCHER_ENABLED) {
            startActivity(
                Intent(this, SplashActivity::class.java).apply {
                    data = intent?.data
                    putExtras(intent ?: Intent())
                },
            )
            finish()
            return
        }

        // Flag enabled locally — full Compose host wiring happens at launcher flip.
        startActivity(Intent(this, SplashActivity::class.java))
        finish()
    }
}
