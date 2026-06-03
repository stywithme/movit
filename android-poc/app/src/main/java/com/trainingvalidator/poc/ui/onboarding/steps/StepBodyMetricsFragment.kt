package com.trainingvalidator.poc.ui.onboarding.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentStepBodyMetricsBinding
import com.trainingvalidator.poc.ui.onboarding.OnboardingViewModel
import java.util.Locale
import kotlin.math.roundToInt

/** Step 2 — weight + height via sliders, with kg/lb and cm/ft unit toggles. Stored metric. */
class StepBodyMetricsFragment : Fragment() {

    private var _binding: FragmentStepBodyMetricsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStepBodyMetricsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setupWeight()
        setupHeight()
    }

    private fun setupWeight() {
        val kg = (viewModel.data.value.weightKg ?: DEFAULT_WEIGHT).coerceIn(WEIGHT_MIN, WEIGHT_MAX)
        viewModel.setWeightKg(kg)
        binding.sliderWeight.value = kg
        binding.sliderWeight.addOnChangeListener { _, value, _ ->
            viewModel.setWeightKg(value)
            updateWeightDisplay(value)
        }

        binding.toggleWeightUnit.check(
            if (viewModel.weightUnit == OnboardingViewModel.UNIT_LB) R.id.btnLb else R.id.btnKg
        )
        binding.toggleWeightUnit.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.weightUnit =
                if (checkedId == R.id.btnLb) OnboardingViewModel.UNIT_LB else OnboardingViewModel.UNIT_KG
            updateWeightDisplay(binding.sliderWeight.value)
        }
        updateWeightDisplay(kg)
    }

    private fun setupHeight() {
        val cm = (viewModel.data.value.heightCm ?: DEFAULT_HEIGHT).coerceIn(HEIGHT_MIN, HEIGHT_MAX)
        viewModel.setHeightCm(cm)
        binding.sliderHeight.value = cm
        binding.sliderHeight.addOnChangeListener { _, value, _ ->
            viewModel.setHeightCm(value)
            updateHeightDisplay(value)
        }

        binding.toggleHeightUnit.check(
            if (viewModel.heightUnit == OnboardingViewModel.UNIT_FT) R.id.btnFt else R.id.btnCm
        )
        binding.toggleHeightUnit.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.heightUnit =
                if (checkedId == R.id.btnFt) OnboardingViewModel.UNIT_FT else OnboardingViewModel.UNIT_CM
            updateHeightDisplay(binding.sliderHeight.value)
        }
        updateHeightDisplay(cm)
    }

    private fun updateWeightDisplay(kg: Float) {
        binding.tvWeightValue.text = if (viewModel.weightUnit == OnboardingViewModel.UNIT_LB) {
            getString(R.string.onb_value_lb, (kg * LB_PER_KG).roundToInt())
        } else {
            getString(R.string.onb_value_kg, formatKg(kg))
        }
    }

    private fun updateHeightDisplay(cm: Float) {
        binding.tvHeightValue.text = if (viewModel.heightUnit == OnboardingViewModel.UNIT_FT) {
            val totalInches = (cm / CM_PER_INCH).roundToInt()
            getString(R.string.onb_value_ft, totalInches / 12, totalInches % 12)
        } else {
            getString(R.string.onb_value_cm, cm.roundToInt())
        }
    }

    private fun formatKg(kg: Float): String =
        if (kg % 1f == 0f) kg.toInt().toString() else String.format(Locale.US, "%.1f", kg)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val WEIGHT_MIN = 30f
        private const val WEIGHT_MAX = 200f
        private const val HEIGHT_MIN = 120f
        private const val HEIGHT_MAX = 220f
        private const val DEFAULT_WEIGHT = 70f
        private const val DEFAULT_HEIGHT = 170f
        private const val LB_PER_KG = 2.20462f
        private const val CM_PER_INCH = 2.54f
    }
}
