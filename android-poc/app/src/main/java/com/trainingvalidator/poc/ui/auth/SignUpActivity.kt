package com.trainingvalidator.poc.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivitySignUpBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.GoogleAuthRequest
import com.trainingvalidator.poc.network.GoogleSignInHelper
import com.trainingvalidator.poc.network.RegisterRequest
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.UserDataCleaner
import com.trainingvalidator.poc.ui.main.MainContainerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SignUpActivity - User registration screen
 * 
 * Features:
 * - Email/Password registration
 * - Google Sign Up
 * - Input validation
 */
class SignUpActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignUpBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        binding.btnCreateAccount.setOnClickListener {
            validateAndSignUp()
        }

        binding.btnGoogle.setOnClickListener {
            signUpWithGoogle()
        }

        binding.tvSignIn.setOnClickListener {
            startActivity(Intent(this, SignInActivity::class.java))
            finish()
        }
    }

    private fun validateAndSignUp() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        // Reset errors
        binding.tilName.error = null
        binding.tilEmail.error = null
        binding.tilPassword.error = null
        binding.tilConfirmPassword.error = null

        // Validate name
        if (name.isEmpty()) {
            binding.tilName.error = getString(R.string.error_name_required)
            return
        }

        // Validate email
        if (email.isEmpty()) {
            binding.tilEmail.error = getString(R.string.error_email_required)
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.error = getString(R.string.error_email_invalid)
            return
        }

        // Validate password
        if (password.isEmpty()) {
            binding.tilPassword.error = getString(R.string.error_password_required)
            return
        }
        if (password.length < 6) {
            binding.tilPassword.error = getString(R.string.error_password_short)
            return
        }

        // Validate confirm password
        if (confirmPassword != password) {
            binding.tilConfirmPassword.error = getString(R.string.error_passwords_not_match)
            return
        }

        performSignUp(name, email, password)
    }

    private fun performSignUp(name: String, email: String, password: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.authApi.register(RegisterRequest(name, email, password))
                }

                if (!response.isSuccessful) {
                    Toast.makeText(
                        this@SignUpActivity,
                        getString(R.string.error_auth_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val body = response.body()
                val authData = body?.data
                if (body?.success == true && authData != null) {
                    UserDataCleaner.clearAll(this@SignUpActivity)
                    AuthManager.saveAuthData(this@SignUpActivity, authData)
                    Toast.makeText(this@SignUpActivity, getString(R.string.success), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@SignUpActivity, MainContainerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                } else {
                    Toast.makeText(
                        this@SignUpActivity,
                        body?.error ?: getString(R.string.error_auth_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SignUpActivity,
                    getString(R.string.error_auth_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun signUpWithGoogle() {
        if (!GoogleSignInHelper.isConfigured()) {
            Toast.makeText(this, getString(R.string.google_sign_up_coming_soon), Toast.LENGTH_SHORT)
                .show()
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val googleResult = GoogleSignInHelper.signIn(this@SignUpActivity)
                
                if (googleResult == null) {
                    Toast.makeText(
                        this@SignUpActivity,
                        getString(R.string.error_auth_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Send to backend (same endpoint for login/register with Google)
                val response = withContext(Dispatchers.IO) {
                    ApiClient.authApi.googleAuth(
                        GoogleAuthRequest(
                            idToken = googleResult.idToken,
                            googleId = googleResult.googleId,
                            email = googleResult.email,
                            name = googleResult.displayName,
                            avatarUrl = googleResult.photoUrl
                        )
                    )
                }

                val body = response.body()
                val authData = body?.data
                if (response.isSuccessful && body?.success == true && authData != null) {
                    UserDataCleaner.clearAll(this@SignUpActivity)
                    AuthManager.saveAuthData(this@SignUpActivity, authData)
                    Toast.makeText(this@SignUpActivity, getString(R.string.success), Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@SignUpActivity, MainContainerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                } else {
                    Toast.makeText(
                        this@SignUpActivity,
                        body?.error ?: getString(R.string.error_auth_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SignUpActivity,
                    getString(R.string.error_auth_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnCreateAccount.isEnabled = !isLoading
        binding.btnGoogle.isEnabled = !isLoading
        binding.tvSignIn.isEnabled = !isLoading
        binding.btnBack.isEnabled = !isLoading
    }
}
