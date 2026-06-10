package com.movit

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.movit.navigation.MovitPostLoginNavigator

/**
 * Post-login home placeholder when [movit.shell.launcher.enabled] is false.
 * Redirects to legacy [com.trainingvalidator.poc.ui.main.MainContainerActivity] if opened directly.
 */
class MovitMainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MovitPostLoginNavigator.navigateToHome(this, clearTask = true)
    }
}
