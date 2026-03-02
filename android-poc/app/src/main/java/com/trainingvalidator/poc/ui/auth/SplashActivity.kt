package com.trainingvalidator.poc.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * SplashActivity - App launch screen with logo animation
 * 
 * Shows the app logo with a loading animation, then navigates to:
 * - OnboardingActivity (if first launch)
 * - SignInActivity (if not logged in)
 * - MainActivity (if logged in)
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Follow system dark/light mode preference
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        
        applySavedLocale()
        
        // Edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        startAnimations()
        navigateAfterDelay()
    }

    private fun applySavedLocale() {
        val language = getSharedPreferences("app_prefs", MODE_PRIVATE)
            .getString("language", null)
        if (!language.isNullOrBlank()) {
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(language)
            )
        }
    }

    private fun setupUI() {
        // Set version text
        binding.tvVersion.text = getString(R.string.version_format, "1.0.0")
        
        // Initially hide elements for animation
        binding.logoContainer.alpha = 0f
        binding.logoContainer.scaleX = 0.8f
        binding.logoContainer.scaleY = 0.8f
        binding.progressBar.alpha = 0f
        binding.tvVersion.alpha = 0f
    }

    private fun startAnimations() {
        // Animate logo container
        binding.logoContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(800)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        
        // Animate progress bar with delay
        binding.progressBar.animate()
            .alpha(1f)
            .setStartDelay(600)
            .setDuration(400)
            .start()
        
        // Animate version with delay
        binding.tvVersion.animate()
            .alpha(1f)
            .setStartDelay(800)
            .setDuration(400)
            .start()
    }

    private fun navigateAfterDelay() {
        lifecycleScope.launch {
            delay(2500) // Show splash for 2.5 seconds
            
            // Check if first launch or logged in
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
            val isLoggedIn = prefs.getBoolean("is_logged_in", false)
            
            val nextActivity = when {
                isFirstLaunch -> OnboardingActivity::class.java
                !isLoggedIn -> SignInActivity::class.java
                else -> {
                    // Navigate to main app with bottom navigation
                    com.trainingvalidator.poc.ui.main.MainContainerActivity::class.java
                }
            }
            
            startActivity(Intent(this@SplashActivity, nextActivity))
            finish()
            
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }
    }
}
