package com.movit.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.movit.designsystem.MovitTheme
import com.movit.feature.shell.MovitAppShellRoute

/**
 * Debug-only root host for the Movit App Shell (Phase 03).
 * Not the launcher — open via adb for visual QA.
 */
class MovitShellPilotActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MovitExploreApiBridge.install(applicationContext)
        MovitHomeApiBridge.install(applicationContext)
        enableEdgeToEdge()
        setContent {
            MovitTheme {
                MovitAppShellRoute()
            }
        }
    }
}
