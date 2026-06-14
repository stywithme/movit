package com.movit.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.movit.MovitMainActivity

/**
 * Post-auth home target is always the KMP shell ([MovitMainActivity]).
 */
object MovitPostLoginNavigator {

    fun homeActivityClass(): Class<out Activity> = MovitMainActivity::class.java

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
