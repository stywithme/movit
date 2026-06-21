package com.movit

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.movit.host.attachMovitShellHost

/**
 * Production LAUNCHER for the Movit KMP shell.
 *
 * Auth bootstrap and logout both stay in-shell ([MovitInnerRoute.Auth]).
 */
class MovitMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        attachMovitShellHost(launchIntent = intent)
    }
}
