package com.trainingvalidator.poc.ui.onboarding.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.card.MaterialCardView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentStepExperienceBinding
import com.trainingvalidator.poc.ui.onboarding.OnboardingViewModel

/** Step 3 — resistance-training experience tier + desired sessions per week. */
class StepExperienceFragment : Fragment() {

    private var _binding: FragmentStepExperienceBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStepExperienceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupExperience()
        setupDays()
    }

    private fun setupExperience() {
        binding.cardBeginner.setOnClickListener { selectExperience("beginner") }
        binding.cardIntermediate.setOnClickListener { selectExperience("intermediate") }
        binding.cardAdvanced.setOnClickListener { selectExperience("advanced") }
        viewModel.data.value.resistanceExperience?.let { renderExperience(it) }
    }

    private fun selectExperience(level: String) {
        viewModel.setResistanceExperience(level)
        renderExperience(level)
    }

    private fun renderExperience(level: String) {
        setChecked(binding.cardBeginner, level == "beginner")
        setChecked(binding.cardIntermediate, level == "intermediate")
        setChecked(binding.cardAdvanced, level == "advanced")
    }

    private fun setChecked(card: MaterialCardView, checked: Boolean) {
        card.isChecked = checked
    }

    private fun setupDays() {
        val days = (viewModel.data.value.targetDaysPerWeek ?: DEFAULT_DAYS).coerceIn(1, 7)
        viewModel.setTargetDaysPerWeek(days)
        binding.sliderDays.value = days.toFloat()
        binding.sliderDays.addOnChangeListener { _, value, _ ->
            val d = value.toInt()
            viewModel.setTargetDaysPerWeek(d)
            updateDaysDisplay(d)
        }
        updateDaysDisplay(days)
    }

    private fun updateDaysDisplay(days: Int) {
        binding.tvDaysValue.text = resources.getQuantityString(R.plurals.onb_days_value, days, days)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val DEFAULT_DAYS = 3
    }
}
