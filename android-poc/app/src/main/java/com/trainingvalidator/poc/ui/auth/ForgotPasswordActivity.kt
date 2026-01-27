package com.trainingvalidator.poc.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityForgotPasswordBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.ForgotPasswordRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ForgotPasswordActivity - Password reset request screen
 */
class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnSendLink.setOnClickListener {
            validateAndSendReset()
        }

        binding.tvBackToSignIn.setOnClickListener {
            finish()
        }
    }

    private fun validateAndSendReset() {
        val email = binding.etEmail.text.toString().trim()

        // Reset errors
        binding.tilEmail.error = null

        // Validate email
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_email_required)
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email_invalid)
            return
        }

        sendResetLink(email)
    }

    private fun sendResetLink(email: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.authApi.forgotPassword(ForgotPasswordRequest(email))
                }
                showSuccess()
            } catch (e: Exception) {
                Toast.makeText(
                    this@ForgotPasswordActivity,
                    getString(R.string.error_auth_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun showSuccess() {
        binding.tilEmail.visibility = View.GONE
        binding.labelEmail.visibility = View.GONE
        binding.btnSendLink.visibility = View.GONE
        binding.successContainer.visibility = View.VISIBLE
        Toast.makeText(this, getString(R.string.reset_link_sent), Toast.LENGTH_SHORT).show()
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnSendLink.isEnabled = !isLoading
        binding.btnBack.isEnabled = !isLoading
        binding.tvBackToSignIn.isEnabled = !isLoading
    }
}
