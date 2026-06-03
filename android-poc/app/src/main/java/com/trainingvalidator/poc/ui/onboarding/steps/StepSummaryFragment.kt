package com.trainingvalidator.poc.ui.onboarding.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentStepSummaryBinding
import com.trainingvalidator.poc.ui.onboarding.OnboardingData
import com.trainingvalidator.poc.ui.onboarding.OnboardingViewModel
import java.util.Locale
import kotlin.math.roundToInt

/** Step 7 — review collected answers and accept the health disclaimer before submitting. */
class StepSummaryFragment : Fragment() {

    private var _binding: FragmentStepSummaryBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStepSummaryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        bindSummary()
        binding.checkDisclaimer.isChecked = viewModel.data.value.healthDisclaimerAccepted
        binding.checkDisclaimer.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setHealthDisclaimerAccepted(isChecked)
        }
    }

    private fun bindSummary() {
        val d = viewModel.data.value
        binding.summaryContainer.removeAllViews()

        d.ageYears?.let { addRow(getString(R.string.onb_age_label), it.toString()) }
        d.biologicalSex?.let { addRow(getString(R.string.onb_gender_label), genderLabel(it)) }
        d.weightKg?.let {
            addRow(getString(R.string.onb_weight_label), getString(R.string.onb_value_kg, formatKg(it)))
        }
        d.heightCm?.let {
            addRow(getString(R.string.onb_height_label), getString(R.string.onb_value_cm, it.roundToInt()))
        }
        d.resistanceExperience?.let {
            addRow(getString(R.string.onb_experience_row), experienceLabel(it))
        }
        d.trainingGoal?.let { addRow(getString(R.string.onb_goal_row), goalLabel(it)) }
        if (d.trainingWeekdays.isNotEmpty()) {
            addRow(getString(R.string.onb_days_row), weekdaysSummary(d.trainingWeekdays))
        }
        d.trainingLocation?.let { addRow(getString(R.string.onb_location_row), locationLabel(it)) }
        addRow(getString(R.string.onb_equipment_row), equipmentSummary(d))
    }

    private fun addRow(label: String, value: String) {
        val row = layoutInflater.inflate(R.layout.view_summary_row, binding.summaryContainer, false)
        row.findViewById<TextView>(R.id.tvRowLabel).text = label
        row.findViewById<TextView>(R.id.tvRowValue).text = value
        binding.summaryContainer.addView(row)
    }

    private fun genderLabel(code: String): String =
        getString(if (code == "male") R.string.onb_gender_male else R.string.onb_gender_female)

    private fun experienceLabel(code: String): String = getString(
        when (code) {
            "beginner" -> R.string.onb_exp_beginner_title
            "intermediate" -> R.string.onb_exp_intermediate_title
            else -> R.string.onb_exp_advanced_title
        }
    )

    private fun goalLabel(code: String): String = getString(
        when (code) {
            "STRENGTH" -> R.string.onb_goal_strength_title
            "HYPERTROPHY" -> R.string.onb_goal_hypertrophy_title
            "POWER" -> R.string.onb_goal_power_title
            else -> R.string.onb_goal_health_title
        }
    )

    private fun locationLabel(code: String): String =
        getString(if (code == "gym") R.string.onb_location_gym else R.string.onb_location_home)

    private fun equipmentSummary(d: OnboardingData): String {
        if (d.trainingLocation == "gym") return getString(R.string.onb_equipment_all)
        val labels = (d.availableEquipment + "bodyweight").distinct().map { equipmentLabel(it) }
        return labels.joinToString(", ")
    }

    private fun equipmentLabel(code: String): String {
        val resId = when (code) {
            "bodyweight" -> R.string.onb_equip_bodyweight
            "dumbbell" -> R.string.onb_equip_dumbbell
            "bands" -> R.string.onb_equip_bands
            "kettlebell" -> R.string.onb_equip_kettlebell
            "pull_up_bar" -> R.string.onb_equip_pull_up_bar
            "bench" -> R.string.onb_equip_bench
            "mat" -> R.string.onb_equip_mat
            else -> return code
        }
        return getString(resId)
    }

    private fun weekdaysSummary(days: Set<Int>): String {
        val names = resources.getStringArray(R.array.onb_weekdays)
        return days.sorted().joinToString(", ") { names.getOrElse(it) { "" } }
    }

    private fun formatKg(kg: Float): String =
        if (kg % 1f == 0f) kg.toInt().toString() else String.format(Locale.US, "%.1f", kg)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
