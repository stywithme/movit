package com.trainingvalidator.poc.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.widget.ViewPager2
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityProfileOnboardingBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.storage.AuthManager
import com.movit.navigation.MovitPostLoginNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ProfileOnboardingActivity — multi-step trainee profiling flow collected right after
 * sign-up (or for existing users with an incomplete TrainingProfile).
 *
 * Steps: age/sex → metrics → experience → goal → weekdays → location/equipment → summary.
 * State lives in [OnboardingViewModel]; the final step submits PUT /mobile/training-profile.
 */
class ProfileOnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileOnboardingBinding
    private val viewModel: OnboardingViewModel by viewModels()

    private var isSubmitting = false

    private val currentStep: Int get() = binding.viewPager.currentItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityProfileOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupPager()
        setupListeners()
        observeState()
        renderForStep(0)
    }

    private fun setupPager() {
        binding.viewPager.adapter = OnboardingPagerAdapter(this)
        binding.viewPager.isUserInputEnabled = false
        binding.viewPager.offscreenPageLimit = 1
        binding.progressIndicator.max = OnboardingViewModel.STEP_COUNT
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                renderForStep(position)
            }
        })
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { goBackOrFinish() }
        binding.btnNext.setOnClickListener { goNextOrSubmit() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBackOrFinish()
            }
        })
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.data.collect { updateNextButtonEnabled() }
            }
        }
    }

    private fun goNextOrSubmit() {
        if (!viewModel.isStepValid(currentStep)) return
        if (currentStep == OnboardingViewModel.STEP_COUNT - 1) {
            submit()
        } else {
            binding.viewPager.setCurrentItem(currentStep + 1, true)
        }
    }

    private fun goBackOrFinish() {
        if (isSubmitting) return
        if (currentStep > 0) {
            binding.viewPager.setCurrentItem(currentStep - 1, true)
        } else {
            finish()
        }
    }

    private fun renderForStep(step: Int) {
        binding.tvStepCounter.text =
            getString(R.string.onb_step_counter, step + 1, OnboardingViewModel.STEP_COUNT)
        binding.progressIndicator.setProgressCompat(step + 1, true)
        binding.btnBack.visibility = if (step == 0) View.INVISIBLE else View.VISIBLE

        val isLast = step == OnboardingViewModel.STEP_COUNT - 1
        binding.btnNext.text = getString(if (isLast) R.string.onb_start_journey else R.string.onb_continue)
        binding.btnNext.setIconResource(if (isLast) 0 else R.drawable.ic_arrow_forward)
        updateNextButtonEnabled()
    }

    private fun updateNextButtonEnabled() {
        binding.btnNext.isEnabled = !isSubmitting && viewModel.isStepValid(currentStep)
        binding.btnNext.alpha = if (binding.btnNext.isEnabled) 1f else 0.5f
    }

    private fun submit() {
        if (isSubmitting) return
        val authHeader = AuthManager.getAuthHeader(this)
        if (authHeader == null) {
            goToMain()
            return
        }

        isSubmitting = true
        binding.btnNext.isEnabled = false
        binding.btnNext.text = getString(R.string.onb_saving)
        val payload = viewModel.data.value.toPayload()

        lifecycleScope.launch {
            val ok = try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.putTrainingProfile(authHeader, payload)
                }
                response.isSuccessful && response.body()?.success == true
            } catch (_: Exception) {
                false
            }

            if (ok) {
                AuthManager.setOnboardingCompleted(this@ProfileOnboardingActivity, true)
                goToMain()
            } else {
                isSubmitting = false
                android.widget.Toast.makeText(
                    this@ProfileOnboardingActivity,
                    getString(R.string.onb_save_failed),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                renderForStep(currentStep)
            }
        }
    }

    private fun goToMain() {
        MovitPostLoginNavigator.navigateToHome(this, clearTask = true)
    }

    companion object {
        fun createIntent(context: Context): Intent =
            Intent(context, ProfileOnboardingActivity::class.java)
    }
}
