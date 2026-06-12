package com.movit.host



import android.content.Intent

import androidx.activity.ComponentActivity

import androidx.activity.compose.setContent

import androidx.activity.enableEdgeToEdge

import com.movit.designsystem.platform.installMovitCoilImageLoader
import com.movit.feature.shell.MovitAppShellHost

import com.trainingvalidator.poc.ui.auth.SplashActivity

import com.trainingvalidator.poc.ui.subscription.SubscriptionActivity



/**

 * Unified Movit shell host for production ([com.movit.MovitMainActivity]) and debug pilot

 * ([com.movit.debug.MovitShellPilotActivity]).

 */

fun ComponentActivity.attachMovitShellHost(
    exitToLegacyAuthOnLogout: Boolean,
    trainingKmpEnabled: Boolean,
    launchIntent: android.content.Intent? = null,
) {
    MovitShellDeepLinkParser.applyFromIntent(launchIntent)

    installMovitCoilImageLoader(applicationContext)

    MovitDataInstall.install(

        context = applicationContext,

        trainingKmpEnabled = trainingKmpEnabled,

    )

    enableEdgeToEdge()



    setContent {

        MovitAppShellHost(

            legacyAuthExitEnabled = exitToLegacyAuthOnLogout,

            onHostBackPressed = { finish() },

            onLaunchLegacySubscription = {

                startActivity(Intent(this@attachMovitShellHost, SubscriptionActivity::class.java))

                true

            },

            onNavigateToLegacyAuth = {

                navigateToLegacyAuth()

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



private fun ComponentActivity.navigateToLegacyAuth() {

    startActivity(

        Intent(this, SplashActivity::class.java).apply {

            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        },

    )

    finish()

}

