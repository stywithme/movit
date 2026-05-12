package com.trainingvalidator.poc.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivitySignInBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.GoogleAuthRequest
import com.trainingvalidator.poc.network.GoogleSignInHelper
import com.trainingvalidator.poc.network.LoginRequest
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.UserDataCleaner
import com.trainingvalidator.poc.ui.main.MainContainerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * SignInActivity - User authentication screen
 * 
 * Features:
 * - Email/Password sign in
 * - Google Sign In
 * - Navigation to Sign Up and Forgot Password
 */
class SignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySignInBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivitySignInBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupListeners()
    }

    private fun setupListeners() {
        binding.btnSignIn.setOnClickListener {
            validateAndSignIn()
        }

        binding.btnGoogle.setOnClickListener {
            signInWithGoogle()
        }

        binding.tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        binding.tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
            finish()
        }
    }

    private fun validateAndSignIn() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        // Reset errors
        binding.tilEmail.error = null
        binding.tilPassword.error = null

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

        performSignIn(email, password)
    }

    private fun performSignIn(email: String, password: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.authApi.login(LoginRequest(email, password))
                }

                if (!response.isSuccessful) {
                    Toast.makeText(
                        this@SignInActivity,
                        getString(R.string.error_auth_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                val body = response.body()
                val authData = body?.data
                if (body?.success == true && authData != null) {
                    UserDataCleaner.clearAll(this@SignInActivity)
                    AuthManager.saveAuthData(this@SignInActivity, authData)
                    startActivity(Intent(this@SignInActivity, MainContainerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                } else {
                    Toast.makeText(
                        this@SignInActivity,
                        body?.error ?: getString(R.string.error_auth_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SignInActivity,
                    getString(R.string.error_auth_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun signInWithGoogle() {
        if (!GoogleSignInHelper.isConfigured()) {
            Toast.makeText(this, getString(R.string.google_sign_in_coming_soon), Toast.LENGTH_SHORT)
                .show()
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val googleResult = GoogleSignInHelper.signIn(this@SignInActivity)
                
                if (googleResult == null) {
                    Toast.makeText(
                        this@SignInActivity,
                        getString(R.string.error_auth_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                    return@launch
                }

                // Send to backend
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
                    UserDataCleaner.clearAll(this@SignInActivity)
                    AuthManager.saveAuthData(this@SignInActivity, authData)
                    startActivity(Intent(this@SignInActivity, MainContainerActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    finish()
                } else {
                    Toast.makeText(
                        this@SignInActivity,
                        body?.error ?: getString(R.string.error_auth_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    this@SignInActivity,
                    getString(R.string.error_auth_failed),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.btnSignIn.isEnabled = !isLoading
        binding.btnGoogle.isEnabled = !isLoading
        binding.tvForgotPassword.isEnabled = !isLoading
        binding.tvSignUp.isEnabled = !isLoading
    }
}
