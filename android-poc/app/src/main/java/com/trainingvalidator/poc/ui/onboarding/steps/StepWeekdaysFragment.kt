package com.trainingvalidator.poc.ui.onboarding.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.chip.Chip
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentStepWeekdaysBinding
import com.trainingvalidator.poc.ui.onboarding.OnboardingViewModel

/** Step 5 — which weekdays the user trains (0=Sun … 6=Sat). Multi-select. */
class StepWeekdaysFragment : Fragment() {

    private var _binding: FragmentStepWeekdaysBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    private val selectedDays = mutableSetOf<Int>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStepWeekdaysBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        selectedDays.clear()
        selectedDays.addAll(viewModel.data.value.trainingWeekdays)

        val names = resources.getStringArray(R.array.onb_weekdays)
        for (index in names.indices) {
            val chip = layoutInflater.inflate(
                R.layout.view_weekday_chip, binding.chipGroupDays, false
            ) as Chip
            chip.text = names[index]
            chip.isChecked = selectedDays.contains(index)
            chip.setOnClickListener {
                if (chip.isChecked) selectedDays.add(index) else selectedDays.remove(index)
                viewModel.setTrainingWeekdays(selectedDays.toSet())
                updateHint()
            }
            binding.chipGroupDays.addView(chip)
        }
        updateHint()
    }

    private fun updateHint() {
        val target = viewModel.data.value.targetDaysPerWeek ?: 0
        binding.tvWeekdaysHint.text =
            getString(R.string.onb_weekdays_hint, selectedDays.size, target)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
