package com.trainingvalidator.poc.ui.onboarding.steps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.trainingvalidator.poc.databinding.FragmentStepGoalBinding
import com.trainingvalidator.poc.ui.onboarding.OnboardingViewModel

/** Step 4 — primary training goal (maps to User.trainingGoal enum). */
class StepGoalFragment : Fragment() {

    private var _binding: FragmentStepGoalBinding? = null
    private val binding get() = _binding!!
    private val viewModel: OnboardingViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStepGoalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.cardStrength.setOnClickListener { selectGoal("STRENGTH") }
        binding.cardHypertrophy.setOnClickListener { selectGoal("HYPERTROPHY") }
        binding.cardPower.setOnClickListener { selectGoal("POWER") }
        binding.cardHealth.setOnClickListener { selectGoal("GENERAL_HEALTH") }
        viewModel.data.value.trainingGoal?.let { renderGoal(it) }
    }

    private fun selectGoal(goal: String) {
        viewModel.setTrainingGoal(goal)
        renderGoal(goal)
    }

    private fun renderGoal(goal: String) {
        binding.cardStrength.isChecked = goal == "STRENGTH"
        binding.cardHypertrophy.isChecked = goal == "HYPERTROPHY"
        binding.cardPower.isChecked = goal == "POWER"
        binding.cardHealth.isChecked = goal == "GENERAL_HEALTH"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
