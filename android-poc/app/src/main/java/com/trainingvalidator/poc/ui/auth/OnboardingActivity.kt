package com.trainingvalidator.poc.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityOnboardingBinding

/**
 * OnboardingActivity - Introduction screens for first-time users
 * 
 * Shows 3 pages explaining the app features:
 * 1. AI Form Analysis
 * 2. Camera Setup
 * 3. Track Progress
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private var currentPage = 0
    private val totalPages = 3

    private val titles by lazy {
        listOf(
            getString(R.string.onboarding_title_1),
            getString(R.string.onboarding_title_2),
            getString(R.string.onboarding_title_3)
        )
    }

    private val descriptions by lazy {
        listOf(
            getString(R.string.onboarding_desc_1),
            getString(R.string.onboarding_desc_2),
            getString(R.string.onboarding_desc_3)
        )
    }

    private val dots by lazy {
        listOf(binding.dot1, binding.dot2, binding.dot3)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupListeners()
        updatePage()
    }

    private fun setupListeners() {
        binding.btnNext.setOnClickListener {
            if (currentPage < totalPages - 1) {
                currentPage++
                updatePage()
            } else {
                finishOnboarding()
            }
        }

        binding.btnBack.setOnClickListener {
            if (currentPage > 0) {
                currentPage--
                updatePage()
            }
        }

        binding.tvSkip.setOnClickListener {
            finishOnboarding()
        }
    }

    private fun updatePage() {
        // Update content with animation
        binding.contentContainer.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                binding.tvTitle.text = titles[currentPage]
                binding.tvDescription.text = descriptions[currentPage]
                binding.contentContainer.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()

        // Update dots
        dots.forEachIndexed { index, dot ->
            dot.setBackgroundResource(
                if (index == currentPage) R.drawable.indicator_dot_active
                else R.drawable.indicator_dot_inactive
            )
        }

        // Update back button visibility
        binding.btnBack.visibility = if (currentPage > 0) View.VISIBLE else View.INVISIBLE

        // Update button text for last page
        if (currentPage == totalPages - 1) {
            binding.btnNext.text = getString(R.string.get_started)
            binding.btnNext.setIconResource(0) // Remove icon
            binding.tvSkip.visibility = View.INVISIBLE
        } else {
            binding.btnNext.text = getString(R.string.next)
            binding.btnNext.setIconResource(R.drawable.ic_arrow_forward)
            binding.tvSkip.visibility = View.VISIBLE
        }
    }

    private fun finishOnboarding() {
        // Mark onboarding as complete
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putBoolean("is_first_launch", false)
            .apply()
        
        // Navigate to Sign In
        startActivity(Intent(this, SignInActivity::class.java))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
