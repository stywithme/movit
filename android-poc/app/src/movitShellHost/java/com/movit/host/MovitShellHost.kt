package com.movit.host

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.movit.MovitMainActivity
import com.movit.billing.SubscriptionActivity
import com.movit.designsystem.platform.installMovitCoilImageLoader
import com.movit.feature.shell.MovitAppShellHost

/**
 * Unified Movit shell host for production ([com.movit.MovitMainActivity]) and debug pilot
 * ([com.movit.debug.MovitShellPilotActivity]).
 */
fun ComponentActivity.attachMovitShellHost(
    legacyAuthExitEnabled: Boolean = false,
    launchIntent: android.content.Intent? = null,
) {
    MovitShellDeepLinkParser.applyFromIntent(launchIntent)

    installMovitCoilImageLoader(applicationContext)

    MovitDataInstall.install(context = applicationContext)

    enableEdgeToEdge()

    setContent {
        MovitAppShellHost(
            legacyAuthExitEnabled = legacyAuthExitEnabled,
            onHostBackPressed = { finish() },
            onLaunchLegacySubscription = {
                startActivity(Intent(this@attachMovitShellHost, SubscriptionActivity::class.java))
                true
            },
            onNavigateToLegacyAuth = {
                restartShellForAuth()
                true
            },
            onShareText = { subject, text ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(shareIntent, subject))
                true
            },
        )
    }
}

private fun ComponentActivity.restartShellForAuth() {
    startActivity(
        Intent(this, MovitMainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        },
    )
    finish()
}
