package com.trainingvalidator.poc.ui.onboarding.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentStepAgeGenderBinding
import com.trainingvalidator.poc.ui.onboarding.OnboardingViewModel

/** Step 1 — age (number picker) + biological sex (choice cards). */
class StepAgeGenderFragment : Fragment() {

    private var _binding: FragmentStepAgeGenderBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStepAgeGenderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupAgePicker()
        setupGender()
    }

    private fun setupAgePicker() {
        val current = viewModel.data.value.ageYears
        binding.agePicker.apply {
            minValue = MIN_AGE
            maxValue = MAX_AGE
            wrapSelectorWheel = false
            value = current ?: DEFAULT_AGE
            setOnValueChangedListener { _, _, newVal -> viewModel.setAge(newVal) }
        }
        if (current == null) viewModel.setAge(binding.agePicker.value)
    }

    private fun setupGender() {
        binding.cardMale.setOnClickListener { selectGender("male") }
        binding.cardFemale.setOnClickListener { selectGender("female") }
        viewModel.data.value.biologicalSex?.let { renderGender(it) }
    }

    private fun selectGender(sex: String) {
        viewModel.setBiologicalSex(sex)
        renderGender(sex)
    }

    private fun renderGender(sex: String) {
        val maleSelected = sex == "male"
        binding.cardMale.isChecked = maleSelected
        binding.cardFemale.isChecked = !maleSelected
        tint(binding.iconMale, maleSelected)
        tint(binding.iconFemale, !maleSelected)
    }

    private fun tint(icon: ImageView, selected: Boolean) {
        val color = ContextCompat.getColor(
            requireContext(),
            if (selected) R.color.primary else R.color.text_secondary
        )
        icon.setColorFilter(color)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val MIN_AGE = 13
        private const val MAX_AGE = 90
        private const val DEFAULT_AGE = 25
    }
}
