package com.movit.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.movit.MovitMainActivity
import com.trainingvalidator.poc.BuildConfig
import com.trainingvalidator.poc.ui.main.MainContainerActivity

/**
 * Strategy B (Phase 06 G-4): legacy [SplashActivity] stays LAUNCHER; only the
 * post-auth home target flips behind [BuildConfig.MOVIT_SHELL_LAUNCHER_ENABLED].
 *
 * Deep links and push notifications are unchanged — they still open legacy activities
 * (e.g. [com.trainingvalidator.poc.ui.subscription.SubscriptionActivity] via
 * `waytofix://subscription/result`) until a dedicated shell routing pass lands.
 */
object MovitPostLoginNavigator {

    fun homeActivityClass(): Class<out Activity> =
        if (BuildConfig.MOVIT_SHELL_LAUNCHER_ENABLED) {
            MovitMainActivity::class.java
        } else {
            MainContainerActivity::class.java
        }

    fun createHomeIntent(context: Context, clearTask: Boolean = false): Intent =
        Intent(context, homeActivityClass()).apply {
            if (clearTask) {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }

    fun navigateToHome(context: Context, clearTask: Boolean = false) {
        context.startActivity(createHomeIntent(context, clearTask))
        if (context is Activity) {
            context.finish()
        }
    }
}
