package com.trainingvalidator.poc.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentProfileBinding
import com.trainingvalidator.poc.network.ApiClient
import com.trainingvalidator.poc.network.LogoutRequest
import com.trainingvalidator.poc.network.UpdateSettingsRequest
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.ui.auth.SignInActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * ProfileFragment - User profile and settings
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        loadUserData()
        setupListeners()
        updateLanguageDisplay()
        fetchProfile()
    }

    private fun loadUserData() {
        val name = AuthManager.getUserName(requireContext(), "Mahmoud Hassan")
        val email = AuthManager.getUserEmail(requireContext(), "alustadh.manager@gmail.com")

        binding.tvUserName.text = name
        binding.tvUserEmail.text = email

        binding.switchVoice.isChecked = AuthManager.getVoiceFeedbackEnabled(requireContext(), true)
        binding.tvWorkoutsCount.text = AuthManager.getTotalWorkouts(requireContext()).toString()
        binding.tvMinutesCount.text = AuthManager.getTotalMinutes(requireContext()).toString()
    }

    private fun setupListeners() {
        binding.btnManageSubscription.setOnClickListener {
            Toast.makeText(requireContext(), getString(R.string.coming_soon), Toast.LENGTH_SHORT)
                .show()
        }

        binding.itemGeneral.setOnClickListener {
            Toast.makeText(
                requireContext(),
                getString(R.string.general_settings_coming_soon),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.itemNotifications.setOnClickListener {
            Toast.makeText(
                requireContext(),
                getString(R.string.notifications_settings_coming_soon),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.itemLanguage.setOnClickListener {
            showLanguageDialog()
        }

        binding.itemLogOut.setOnClickListener {
            showLogoutConfirmation()
        }

        binding.switchVoice.setOnCheckedChangeListener { _, isChecked ->
            AuthManager.updateSettings(requireContext(), voiceFeedback = isChecked)
            updateRemoteSettings(voiceFeedback = isChecked)
        }

        binding.btnEditAvatar.setOnClickListener {
            Toast.makeText(
                requireContext(),
                getString(R.string.change_avatar_coming_soon),
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnMenu.setOnClickListener {
            // Menu options
        }
    }

    private fun updateLanguageDisplay() {
        val currentLanguage = getCurrentLanguage()
        binding.tvCurrentLanguage.text = if (currentLanguage == "ar") {
            getString(R.string.language_arabic)
        } else {
            getString(R.string.language_english)
        }
    }

    private fun getCurrentLanguage(): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        val locale = if (appLocales.isEmpty) {
            resources.configuration.locales[0]
        } else {
            appLocales[0]
        }
        return locale?.language ?: "en"
    }

    private fun showLanguageDialog() {
        val languages = arrayOf(
            getString(R.string.language_english),
            getString(R.string.language_arabic)
        )
        val currentIndex = if (getCurrentLanguage() == "ar") 1 else 0

        AlertDialog.Builder(requireContext(), R.style.Theme_WayToFix_Dialog)
            .setTitle(R.string.language)
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                val newLocale = if (which == 1) Locale("ar") else Locale("en")
                setAppLocale(newLocale)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setAppLocale(locale: Locale) {
        // Save preference first
        AuthManager.updateSettings(requireContext(), preferredLanguage = locale.language)
        updateRemoteSettings(preferredLanguage = locale.language)

        // Set app locale using AppCompatDelegate
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(locale.toLanguageTag())
        )

        // Restart the app to apply changes properly
        val intent = Intent(requireContext(), MainContainerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(MainContainerActivity.EXTRA_START_TAB, R.id.nav_profile)
        }
        startActivity(intent)
        activity?.finish()
    }

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(requireContext(), R.style.Theme_WayToFix_Dialog)
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
            val refreshToken = AuthManager.getRefreshToken(requireContext())
            if (!refreshToken.isNullOrBlank()) {
                try {
                    withContext(Dispatchers.IO) {
                        ApiClient.authApi.logout(LogoutRequest(refreshToken))
                    }
                } catch (_: Exception) {
                    // Best-effort logout
                }
            }

            AuthManager.clearAuthData(requireContext())
            startActivity(Intent(requireContext(), SignInActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            activity?.finish()
        }
    }

    private fun fetchProfile() {
        val authHeader = AuthManager.getAuthHeader(requireContext()) ?: return
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.authApi.getProfile(authHeader)
                }
                val body = response.body()
                val user = body?.data
                if (response.isSuccessful && body?.success == true && user != null) {
                    AuthManager.updateUser(requireContext(), user)
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
        val authHeader = AuthManager.getAuthHeader(requireContext()) ?: return
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
