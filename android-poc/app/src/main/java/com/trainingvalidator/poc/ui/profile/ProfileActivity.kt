package com.trainingvalidator.poc.ui.profile

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityProfileBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.LogoutRequest
import com.trainingvalidator.poc.network.UpdateSettingsRequest
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.UserDataCleaner
import com.trainingvalidator.poc.ui.auth.SignInActivity
import com.trainingvalidator.poc.ui.debug.DebugActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * ProfileActivity - User profile and settings screen
 * 
 * Features:
 * - Display user info
 * - Pro membership status
 * - Lifetime stats
 * - Settings (Voice feedback, Notifications, Language)
 * - Log out
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        loadUserData()
        setupListeners()
        updateLanguageDisplay()
        fetchProfile()
    }

    private fun loadUserData() {
        val name = AuthManager.getUserName(this, "Mahmoud Hassan")
        val email = AuthManager.getUserEmail(this, "alustadh.manager@gmail.com")

        binding.tvUserName.text = name
        binding.tvUserEmail.text = email

        binding.switchVoice.isChecked = AuthManager.getVoiceFeedbackEnabled(this, true)
        binding.tvWorkoutsCount.text = AuthManager.getTotalWorkouts(this).toString()
        binding.tvMinutesCount.text = AuthManager.getTotalMinutes(this).toString()
    }

    private fun setupListeners() {
        binding.btnManageSubscription.setOnClickListener {
            Toast.makeText(this, getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
        }

        binding.itemGeneral.setOnClickListener {
            Toast.makeText(
                this,
                getString(R.string.general_settings_coming_soon),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.itemNotifications.setOnClickListener {
            Toast.makeText(
                this,
                getString(R.string.notifications_settings_coming_soon),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.itemLanguage.setOnClickListener {
            showLanguageDialog()
        }

        binding.itemDebug.setOnClickListener {
            startActivity(Intent(this, DebugActivity::class.java))
        }

        binding.itemLogOut.setOnClickListener {
            showLogoutConfirmation()
        }

        binding.switchVoice.setOnCheckedChangeListener { _, isChecked ->
            AuthManager.updateSettings(this, voiceFeedback = isChecked)
            updateRemoteSettings(voiceFeedback = isChecked)
        }

        binding.btnEditAvatar.setOnClickListener {
            Toast.makeText(
                this,
                getString(R.string.change_avatar_coming_soon),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnMenu.setOnClickListener {
            // Menu options
        }
    }

    private fun updateLanguageDisplay() {
        val currentLanguage = currentLanguage
        binding.tvCurrentLanguage.text = if (currentLanguage == "ar") {
            getString(R.string.language_arabic)
        } else {
            getString(R.string.language_english)
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(
            getString(R.string.language_english),
            getString(R.string.language_arabic)
        )
        val currentIndex = if (currentLanguage == "ar") 1 else 0

        AlertDialog.Builder(this, R.style.Theme_WayToFix_Dialog)
            .setTitle(R.string.language)
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val newLocale = if (which == 1) Locale.forLanguageTag("ar") else Locale.forLanguageTag("en")
                setAppLocale(newLocale)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setAppLocale(locale: Locale) {
        // Save preference first
        AuthManager.updateSettings(this, preferredLanguage = locale.language)
        updateRemoteSettings(preferredLanguage = locale.language)

        // Set app locale using AppCompatDelegate
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(locale.toLanguageTag())
        )

        // Restart this activity to apply changes
        val intent = Intent(this, ProfileActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this, R.style.Theme_WayToFix_Dialog)
            .setTitle(R.string.log_out)
            .setMessage(R.string.log_out_confirm)
            .setPositiveButton(R.string.log_out) { _, _ ->
                logout()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun logout() {
        lifecycleScope.launch {
            val refreshToken = AuthManager.getRefreshToken(this@ProfileActivity)
            if (!refreshToken.isNullOrBlank()) {
                try {
                    withContext(Dispatchers.IO) {
                        ApiClient.authApi.logout(LogoutRequest(refreshToken))
                    }
                } catch (_: Exception) {
                    // Best-effort logout
                }
            }

            // Clear all user-specific caches before auth data to prevent data leakage
            UserDataCleaner.clearAll(this@ProfileActivity)
            AuthManager.clearAuthData(this@ProfileActivity)
            startActivity(Intent(this@ProfileActivity, SignInActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun fetchProfile() {
        val authHeader = AuthManager.getAuthHeader(this) ?: return
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.authApi.getProfile(authHeader)
                }
                val body = response.body()
                val user = body?.data
                if (response.isSuccessful && body?.success == true && user != null) {
                    AuthManager.updateUser(this@ProfileActivity, user)
                    loadUserData()
                }
            } catch (_: Exception) {
                // Ignore profile refresh errors
            }
        }
    }

    private fun updateRemoteSettings(
        preferredLanguage: String? = null,
        voiceFeedback: Boolean? = null
    ) {
        val authHeader = AuthManager.getAuthHeader(this) ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ApiClient.authApi.updateSettings(
                        authHeader,
                        UpdateSettingsRequest(
                            preferredLanguage = preferredLanguage,
                            voiceFeedback = voiceFeedback
                        )
                    )
                }
            } catch (_: Exception) {
                // Best-effort update
            }
        }
    }
}
