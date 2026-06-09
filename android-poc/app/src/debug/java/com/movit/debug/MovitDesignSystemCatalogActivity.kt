package com.movit.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.movit.designsystem.catalog.MovitComponentsTabScreen

/**
 * Debug-only host for visual QA of the Movit Design System.
 * Not registered as launcher; open via adb or debug menu when needed.
 */
class MovitDesignSystemCatalogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Catalog applies MovitTheme internally (supports light/dark toggle).
            MovitComponentsTabScreen()
        }
    }
}
