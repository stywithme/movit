package com.movit.host

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.movit.billing.SubscriptionActivity
import com.movit.designsystem.platform.installMovitCoilImageLoader
import com.movit.feature.shell.MovitAppShellHost

/**
 * Unified Movit shell host for production ([com.movit.MovitMainActivity]) and debug pilot
 * ([com.movit.debug.MovitShellPilotActivity]).
 */
fun ComponentActivity.attachMovitShellHost(
    launchIntent: android.content.Intent? = null,
) {
    MovitShellDeepLinkParser.applyFromIntent(launchIntent)

    installMovitCoilImageLoader(applicationContext)

    MovitDataInstall.install(context = applicationContext)

    enableEdgeToEdge()
    WindowCompat.setDecorFitsSystemWindows(window, false)
    applyImmersiveNavigationBar()
    lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                applyImmersiveNavigationBar()
            }
        },
    )

    setContent {
        MovitAppShellHost(
            onHostBackPressed = { finish() },
            onLaunchLegacySubscription = {
                startActivity(Intent(this@attachMovitShellHost, SubscriptionActivity::class.java))
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

private fun ComponentActivity.applyImmersiveNavigationBar() {
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.navigationBars())
        systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
