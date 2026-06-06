package com.trainingvalidator.poc.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AutoCompleteTextView
import android.widget.CompoundButton
import android.widget.EditText
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
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.trainingvalidator.poc.BuildConfig
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
import com.trainingvalidator.poc.ui.subscription.SubscriptionActivity
import com.trainingvalidator.poc.ui.debug.DebugActivity
import com.trainingvalidator.poc.ui.theme.AppThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.text.SimpleDateFormat
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

    private data class TrainingProfileState(
        val trainingGoal: String? = null,
        val availableDaysPerWeek: Int? = null,
        val maxSessionMinutes: Int? = null,
        val availableEquipment: Set<String> = emptySet(),
        val displayMode: String = "beginner"
    )

    private var trainingProfileState = TrainingProfileState()
    private val equipmentOptions = listOf(
        "bodyweight",
        "dumbbell",
        "bands",
        "kettlebell",
        "barbell",
        "machine",
        "cable"
    )

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
        updateThemeModeDisplay()
        trainingProfileState = trainingProfileState.copy(
            displayMode = SettingsManager.getTrainingDisplayMode()
        )
        renderTrainingProfileSummary()
        fetchProfile()
        fetchTrainingProfile()
    }

    override fun onResume() {
        super.onResume()
        binding.ivAvatar.bindUserAvatar(AuthManager.getAvatarUrl(this))
        bindProMembershipUi()
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
        bindProMembershipUi()
    }

    private fun bindProMembershipUi() {
        if (AuthManager.isProUser(this)) {
            binding.tvProMembershipTitle.text = getString(R.string.pro_membership)
            val exp = AuthManager.getSubscriptionExpiryIso(this)
            binding.tvProMembershipSubtitle.text = if (!exp.isNullOrBlank()) {
                getString(R.string.profile_pro_expires, formatProfileExpiryIso(exp))
            } else {
                getString(R.string.pro_membership_desc)
            }
        } else {
            binding.tvProMembershipTitle.text = getString(R.string.profile_membership_free_title)
            binding.tvProMembershipSubtitle.text = getString(R.string.profile_membership_free_desc)
        }
    }

    private fun formatProfileExpiryIso(iso: String): String {
        return try {
            val cleaned = iso.take(19).replace('T', ' ')
            val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
            val d = parser.parse(cleaned) ?: return iso
            DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(d)
        } catch (_: Exception) {
            iso
        }
    }

    private fun setupListeners() {
        binding.btnManageSubscription.setOnClickListener {
            startActivity(Intent(this, SubscriptionActivity::class.java))
        }

        binding.itemExerciseSettings.setOnClickListener {
            showExerciseSettingsDialog()
        }

        binding.itemTrainingProfile.setOnClickListener {
            showTrainingProfileDialog()
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

        binding.itemThemeMode.setOnClickListener {
            showThemeModeDialog()
        }

        binding.itemDebug.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        binding.itemDebug.setOnClickListener {
            if (BuildConfig.DEBUG) {
                startActivity(Intent(this, DebugActivity::class.java))
            }
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
     * Same layout as training settings dialog; hides the standalone voice row and camera switch.
     * Persists live coach preferences, indicator type, and MediaPipe model as user defaults ([SettingsManager]).
     */
    private fun showExerciseSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_training_settings, null)
        val dialog = android.app.AlertDialog.Builder(this, R.style.Theme_WayToFix_Dialog)
            .setView(dialogView)
            .create()

        var selectedIndicator = SettingsManager.getIndicatorType()
        var selectedModel = SettingsManager.getModelType()
        var selectedCoachIntensity = SettingsManager.getCoachIntensity()

        fun setChoiceButtonSelected(button: MaterialButton, isSelected: Boolean) {
            if (isSelected) {
                button.setBackgroundColor(ContextCompat.getColor(this, R.color.primary))
                button.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
            } else {
                button.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                button.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            }
        }

        val btnLine = dialogView.findViewById<MaterialButton>(R.id.btnIndicatorLine)
        val btnArc = dialogView.findViewById<MaterialButton>(R.id.btnIndicatorArc)

        fun updateIndicatorButtons() {
            setChoiceButtonSelected(btnLine, selectedIndicator == "line")
            setChoiceButtonSelected(btnArc, selectedIndicator != "line")
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

        val btnCoachCalm = dialogView.findViewById<MaterialButton>(R.id.btnCoachCalm)
        val btnCoachStandard = dialogView.findViewById<MaterialButton>(R.id.btnCoachStandard)
        val btnCoachStrict = dialogView.findViewById<MaterialButton>(R.id.btnCoachStrict)

        fun updateCoachButtons() {
            setChoiceButtonSelected(btnCoachCalm, selectedCoachIntensity == "calm")
            setChoiceButtonSelected(btnCoachStandard, selectedCoachIntensity == "standard")
            setChoiceButtonSelected(btnCoachStrict, selectedCoachIntensity == "strict")
        }
        updateCoachButtons()

        btnCoachCalm.setOnClickListener {
            selectedCoachIntensity = "calm"
            updateCoachButtons()
        }
        btnCoachStandard.setOnClickListener {
            selectedCoachIntensity = "standard"
            updateCoachButtons()
        }
        btnCoachStrict.setOnClickListener {
            selectedCoachIntensity = "strict"
            updateCoachButtons()
        }

        val btnModelFull = dialogView.findViewById<MaterialButton>(R.id.btnModelFull)
        val btnModelHeavy = dialogView.findViewById<MaterialButton>(R.id.btnModelHeavy)

        fun updateModelButtons() {
            setChoiceButtonSelected(btnModelFull, selectedModel == "full")
            setChoiceButtonSelected(btnModelHeavy, selectedModel != "full")
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
        val root = dialogView.findViewById<ViewGroup>(R.id.settingsContent)
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
        dialogView.findViewById<View>(R.id.cameraCueSection).visibility = View.GONE

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
            SettingsManager.setCoachIntensity(selectedCoachIntensity)
            SettingsManager.setCameraCueMode("voice")
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

    private fun updateThemeModeDisplay() {
        val mode = AppThemeManager.getMode(this)
        binding.tvCurrentThemeMode.text = getString(AppThemeManager.labelRes(mode))
    }

    private fun renderTrainingProfileSummary() {
        val parts = mutableListOf<String>()
        trainingProfileState.trainingGoal?.let { parts.add(formatGoalCode(it)) }
        trainingProfileState.availableDaysPerWeek?.let { parts.add("$it d/w") }
        trainingProfileState.maxSessionMinutes?.let { parts.add("$it min") }
        if (trainingProfileState.availableEquipment.isNotEmpty()) {
            parts.add(
                equipmentOptions
                    .filter { trainingProfileState.availableEquipment.contains(it) }
                    .joinToString(", ")
            )
        }
        parts.add(
            if (trainingProfileState.displayMode == "advanced") {
                getString(R.string.display_mode_advanced)
            } else {
                getString(R.string.display_mode_beginner)
            }
        )

        binding.tvTrainingProfileSummary.text = if (parts.isEmpty()) {
            getString(R.string.training_profile_summary_empty)
        } else {
            parts.joinToString(" • ")
        }
    }

    private fun formatGoalCode(code: String): String {
        return code
            .lowercase(Locale.US)
            .split('_')
            .joinToString(" ") { token ->
                token.replaceFirstChar { c -> c.titlecase(Locale.US) }
            }
    }

    private fun parseIntValue(value: Any?): Int? {
        return when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }
    }

    private fun parseStringSet(value: Any?): Set<String> {
        return if (value is List<*>) {
            value.mapNotNull { it?.toString() }.toSet()
        } else {
            emptySet()
        }
    }

    private fun fetchTrainingProfile() {
        val authHeader = AuthManager.getAuthHeader(this) ?: return
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.getTrainingProfile(authHeader)
                }
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    val profile = body.data?.profile.orEmpty()
                    trainingProfileState = TrainingProfileState(
                        trainingGoal = body.data?.trainingGoal,
                        availableDaysPerWeek = parseIntValue(profile["availableDaysPerWeek"]),
                        maxSessionMinutes = parseIntValue(profile["maxSessionMinutes"]),
                        availableEquipment = parseStringSet(profile["availableEquipment"]),
                        displayMode = SettingsManager.getTrainingDisplayMode()
                    )
                    renderTrainingProfileSummary()
                }
            } catch (_: Exception) {
                // Best-effort only
            }
        }
    }

    private fun showTrainingProfileDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_training_profile, null)
        val goalInput = dialogView.findViewById<AutoCompleteTextView>(R.id.inputTrainingGoal)
        val modeInput = dialogView.findViewById<AutoCompleteTextView>(R.id.inputTrainingDisplayMode)
        val daysInput = dialogView.findViewById<EditText>(R.id.inputAvailableDays)
        val minutesInput = dialogView.findViewById<EditText>(R.id.inputMaxSessionMinutes)

        val goalCodes = listOf("", "STRENGTH", "HYPERTROPHY", "POWER", "GENERAL_HEALTH")
        val goalLabels = goalCodes.map { code ->
            if (code.isBlank()) "—" else formatGoalCode(code)
        }
        goalInput.setAdapter(
            android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, goalLabels)
        )

        val modeCodes = listOf("beginner", "advanced")
        val modeLabels = listOf(
            getString(R.string.display_mode_beginner),
            getString(R.string.display_mode_advanced)
        )
        modeInput.setAdapter(
            android.widget.ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, modeLabels)
        )

        val selectedGoalIndex = goalCodes.indexOf(trainingProfileState.trainingGoal ?: "").coerceAtLeast(0)
        goalInput.setText(goalLabels[selectedGoalIndex], false)
        val selectedModeIndex = modeCodes.indexOf(trainingProfileState.displayMode).takeIf { it >= 0 } ?: 0
        modeInput.setText(modeLabels[selectedModeIndex], false)
        daysInput.setText(trainingProfileState.availableDaysPerWeek?.toString().orEmpty())
        minutesInput.setText(trainingProfileState.maxSessionMinutes?.toString().orEmpty())

        val equipmentChecks = mapOf(
            "bodyweight" to dialogView.findViewById<MaterialCheckBox>(R.id.checkEquipmentBodyweight),
            "dumbbell" to dialogView.findViewById<MaterialCheckBox>(R.id.checkEquipmentDumbbell),
            "bands" to dialogView.findViewById<MaterialCheckBox>(R.id.checkEquipmentBands),
            "kettlebell" to dialogView.findViewById<MaterialCheckBox>(R.id.checkEquipmentKettlebell),
            "barbell" to dialogView.findViewById<MaterialCheckBox>(R.id.checkEquipmentBarbell),
            "machine" to dialogView.findViewById<MaterialCheckBox>(R.id.checkEquipmentMachine),
            "cable" to dialogView.findViewById<MaterialCheckBox>(R.id.checkEquipmentCable)
        )
        equipmentChecks.forEach { (code, checkbox) ->
            checkbox.isChecked = trainingProfileState.availableEquipment.contains(code)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.training_profile))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val selectedGoal = goalCodes.getOrElse(goalLabels.indexOf(goalInput.text.toString())) { "" }
                val selectedMode = modeCodes.getOrElse(modeLabels.indexOf(modeInput.text.toString())) { "beginner" }
                val selectedEquipment = equipmentOptions.filter { code ->
                    equipmentChecks[code]?.isChecked == true
                }.toSet()

                trainingProfileState = TrainingProfileState(
                    trainingGoal = selectedGoal.ifBlank { null },
                    availableDaysPerWeek = daysInput.text.toString().toIntOrNull(),
                    maxSessionMinutes = minutesInput.text.toString().toIntOrNull(),
                    availableEquipment = selectedEquipment,
                    displayMode = selectedMode
                )
                SettingsManager.setTrainingDisplayMode(selectedMode)
                renderTrainingProfileSummary()
                saveTrainingProfile()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun saveTrainingProfile() {
        val authHeader = AuthManager.getAuthHeader(this) ?: return
        val payload = mutableMapOf<String, Any?>(
            "availableEquipment" to trainingProfileState.availableEquipment.toList(),
            "availableDaysPerWeek" to trainingProfileState.availableDaysPerWeek,
            "maxSessionMinutes" to trainingProfileState.maxSessionMinutes
        )
        trainingProfileState.trainingGoal?.let { payload["trainingGoal"] = it }

        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    ApiClient.mobileSyncApi.putTrainingProfile(authHeader, payload)
                }
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    Toast.makeText(
                        this@ProfileActivity,
                        getString(R.string.training_profile_saved),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@ProfileActivity,
                        body?.error ?: getString(R.string.training_profile_save_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
                Toast.makeText(
                    this@ProfileActivity,
                    getString(R.string.training_profile_save_failed),
                    Toast.LENGTH_SHORT
                ).show()
            }
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

    private fun showThemeModeDialog() {
        val modes = AppThemeManager.modes
        val labels = modes
            .map { mode -> getString(AppThemeManager.labelRes(mode)) }
            .toTypedArray()
        val currentIndex = modes.indexOf(AppThemeManager.getMode(this)).coerceAtLeast(0)

        AlertDialog.Builder(this, R.style.Theme_WayToFix_Dialog)
            .setTitle(R.string.theme_mode)
            .setSingleChoiceItems(labels, currentIndex) { dialog, which ->
                AppThemeManager.setMode(this, modes[which])
                updateThemeModeDisplay()
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
