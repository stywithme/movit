package com.trainingvalidator.poc.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentHomeBinding
import com.trainingvalidator.poc.ui.TrainingActivity
import java.util.Calendar

/**
 * HomeFragment - Main dashboard with stats and quick actions
 */
class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupGreeting()
        loadUserData()
        setupListeners()
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greetingRes = when {
            hour < 12 -> R.string.greeting_morning
            hour < 17 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        }
        binding.tvGreeting.text = getString(greetingRes)
    }

    private fun loadUserData() {
        val prefs = requireContext().getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val name = prefs.getString("user_name", "Athlete") ?: "Athlete"
        val firstName = name.split(" ").firstOrNull() ?: name
        binding.tvUserName.text = firstName
        
        // Mock stats - Replace with real data
        binding.tvWeeklyWorkouts.text = "5"
        binding.tvFormScore.text = "87%"
        binding.tvStreak.text = "12🔥"
        
        // Last exercise
        binding.tvLastExercise.text = getString(R.string.sample_exercise_squats)
        binding.tvLastProgress.text = getString(R.string.sets_completed_format, 2, 3)
    }

    private fun setupListeners() {
        binding.cardContinue.setOnClickListener {
            // Navigate to last exercise
        }

        binding.btnContinue.setOnClickListener {
            // Start training
            startActivity(Intent(requireContext(), TrainingActivity::class.java))
        }

        binding.cardStartCamera.setOnClickListener {
            // Navigate to exercises
            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_exercises)
        }

        binding.cardAnalyzeVideo.setOnClickListener {
            // Open video picker
        }

        binding.ivAvatar.setOnClickListener {
            // Navigate to profile
            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_profile)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
