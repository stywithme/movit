package com.movit.debug

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.movit.designsystem.MovitTheme
import com.movit.feature.explore.MovitExploreRoute

/**
 * Debug-only pilot host for the Movit Explore screen (Phase 02).
 * Not the launcher — open via adb for visual QA.
 */
class MovitExplorePilotActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MovitExploreApiBridge.install(applicationContext)
        enableEdgeToEdge()
        setContent {
            MovitTheme {
                MovitExploreRoute(
                    onNavigateToExercise = { id ->
                        Toast.makeText(this, "Navigate: $id", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}
