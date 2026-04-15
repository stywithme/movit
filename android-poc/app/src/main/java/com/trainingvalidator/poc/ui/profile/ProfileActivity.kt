package com.trainingvalidator.poc.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.trainingvalidator.poc.ui.utils.bindUserAvatar
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityProfileBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.LogoutRequest
import com.trainingvalidator.poc.network.UpdateSettingsRequest
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.storage.UserDataCleaner
import com.trainingvalidator.poc.training.config.SettingsManager
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
 * - Settings (Exercise defaults, Voice feedback, Notifications, Language)
 * - Log out
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding

    private val voiceSwitchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        SettingsManager.setVoiceFeedbackEnabled(isChecked)
        AuthManager.updateSettings(this@ProfileActivity, voiceFeedback = isChecked)
        updateRemoteSettings(voiceFeedback = isChecked)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        try {
            SettingsManager.initialize(this)
        } catch (_: Exception) {
            // Best-effort; voice/model prefs may still work after partial init
        }

        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadUserData()
        setupListeners()
        updateLanguageDisplay()
        fetchProfile()
    }

    override fun onResume() {
        super.onResume()
        binding.ivAvatar.bindUserAvatar(AuthManager.getAvatarUrl(this))
    }

    private fun loadUserData() {
        val name = AuthManager.getUserName(this, "Mahmoud Hassan")
        val email = AuthManager.getUserEmail(this, "alustadh.manager@gmail.com")

        binding.tvUserName.text = name
        binding.tvUserEmail.text = email

        binding.switchVoice.setOnCheckedChangeListener(null)
        binding.switchVoice.isChecked = SettingsManager.isVoiceFeedbackEnabled()
        binding.switchVoice.setOnCheckedChangeListener(voiceSwitchListener)

        binding.tvWorkoutsCount.text = AuthManager.getTotalWorkouts(this).toString()
        binding.tvMinutesCount.text = AuthManager.getTotalMinutes(this).toString()

        binding.ivAvatar.bindUserAvatar(AuthManager.getAvatarUrl(this))
    }

    private fun setupListeners() {
        binding.btnManageSubscription.setOnClickListener {
            Toast.makeText(this, getString(R.string.coming_soon), Toast.LENGTH_SHORT).show()
        }

        binding.itemExerciseSettings.setOnClickListener {
            showExerciseSettingsDialog()
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

    /**
     * Same layout as training settings dialog; hides voice and camera sections.
     * Persists indicator type and MediaPipe model as user defaults ([SettingsManager]).
     */
    private fun showExerciseSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_training_settings, null)
        val dialog = android.app.AlertDialog.Builder(this, R.style.Theme_WayToFix_Dialog)
            .setView(dialogView)
            .create()

        var selectedIndicator = SettingsManager.getIndicatorType()
        var selectedModel = SettingsManager.getModelType()

        val btnLine = dialogView.findViewById<MaterialButton>(R.id.btnIndicatorLine)
        val btnArc = dialogView.findViewById<MaterialButton>(R.id.btnIndicatorArc)

        fun updateIndicatorButtons() {
            if (selectedIndicator == "line") {
                btnLine.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                btnLine.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
                btnArc.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btnArc.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            } else {
                btnArc.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                btnArc.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
                btnLine.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btnLine.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
        }
        updateIndicatorButtons()

        btnLine.setOnClickListener {
            selectedIndicator = "line"
            updateIndicatorButtons()
        }
        btnArc.setOnClickListener {
            selectedIndicator = "arc"
            updateIndicatorButtons()
        }

        val btnModelFull = dialogView.findViewById<MaterialButton>(R.id.btnModelFull)
        val btnModelHeavy = dialogView.findViewById<MaterialButton>(R.id.btnModelHeavy)

        fun updateModelButtons() {
            if (selectedModel == "full") {
                btnModelFull.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                btnModelFull.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
                btnModelHeavy.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btnModelHeavy.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            } else {
                btnModelHeavy.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                btnModelHeavy.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
                btnModelFull.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                btnModelFull.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
        }
        updateModelButtons()

        btnModelFull.setOnClickListener {
            selectedModel = "full"
            updateModelButtons()
        }
        btnModelHeavy.setOnClickListener {
            selectedModel = "heavy"
            updateModelButtons()
        }

        // Hide voice feedback row and adjacent dividers (standalone switch on profile)
        val switchVoice = dialogView.findViewById<View>(R.id.switchVoiceFeedback)
        val voiceRow = switchVoice.parent as View
        val root = dialogView as ViewGroup
        val voiceIndex = root.indexOfChild(voiceRow)
        if (voiceIndex > 0) {
            root.getChildAt(voiceIndex - 1).visibility = View.GONE
        }
        voiceRow.visibility = View.GONE
        if (voiceIndex + 1 < root.childCount) {
            root.getChildAt(voiceIndex + 1).visibility = View.GONE
        }

        dialogView.findViewById<View>(R.id.cameraSectionContainer).visibility = View.GONE
        dialogView.findViewById<View>(R.id.dividerCamera).visibility = View.GONE

        val headerLayout = root.getChildAt(0) as? ViewGroup
        if (headerLayout != null && headerLayout.childCount >= 2) {
            val titleView = headerLayout.getChildAt(1)
            if (titleView is TextView) {
                titleView.setText(R.string.exercise_settings)
            }
        }

        dialogView.findViewById<View>(R.id.btnCloseSettings).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<MaterialButton>(R.id.btnApplySettings).setOnClickListener {
            SettingsManager.setIndicatorType(selectedIndicator)
            SettingsManager.setModelType(selectedModel)
            dialog.dismiss()
            Toast.makeText(
                this,
                getString(R.string.settings) + " " + getString(R.string.save),
                Toast.LENGTH_SHORT
            ).show()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
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
                    SettingsManager.setVoiceFeedbackEnabled(user.voiceFeedback)
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
