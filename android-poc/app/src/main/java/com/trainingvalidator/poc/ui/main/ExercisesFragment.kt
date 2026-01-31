package com.trainingvalidator.poc.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentExercisesBinding
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.SyncManager
import com.trainingvalidator.poc.training.loader.ExerciseLoader
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.ui.PreWorkoutActivity
import com.trainingvalidator.poc.ui.WorkoutListActivity
import kotlinx.coroutines.launch

/**
 * ExercisesFragment - Exercise library with search and filter
 */
class ExercisesFragment : Fragment() {

    companion object {
        private const val TAG = "ExercisesFragment"
    }

    private var _binding: FragmentExercisesBinding? = null
    private val binding get() = _binding!!
    
    private val exercises = mutableListOf<ExerciseConfig>()
    private val filteredExercises = mutableListOf<ExerciseConfig>()
    private var currentCategory: String? = null
    
    private val repository: ExerciseRepository by lazy { 
        ExerciseRepository.getInstance(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExercisesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        setupListeners()
        initializeRepository()
    }

    private fun setupUI() {
        // Setup RecyclerView with 2-column grid
        binding.rvExercises.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvExercises.adapter = ExerciseAdapter(filteredExercises) { exercise ->
            openExerciseDetail(exercise)
        }
    }

    private fun setupListeners() {
        // Category chips
        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val chipId = checkedIds.firstOrNull()
            currentCategory = when (chipId) {
                R.id.chipLegs -> "legs"
                R.id.chipArms -> "arms"
                R.id.chipCore -> "core"
                R.id.chipBack -> "back"
                R.id.chipChest -> "chest"
                else -> null
            }
            filterExercises()
        }

        // Search
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            filterExercises()
            true
        }

        // View all
        binding.tvViewAll.setOnClickListener {
            binding.chipAll.isChecked = true
        }

        // Featured program
        binding.btnStartProgram.setOnClickListener {
            startActivity(Intent(requireContext(), WorkoutListActivity::class.java))
        }

        // Avatar -> Profile
        binding.ivAvatar.setOnClickListener {
            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_profile)
        }
    }

    private fun initializeRepository() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                
                repository.initialize(autoSync = true)
                loadExercisesFromRepository()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize repository", e)
                loadExercisesFromAssets()
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun loadExercisesFromRepository() {
        val loaded = repository.getAllExercises()
        
        if (loaded.isEmpty()) {
            loadExercisesFromAssets()
        } else {
            updateExercisesList(loaded)
            Log.d(TAG, "Loaded ${loaded.size} exercises from repository")
        }
    }

    private fun loadExercisesFromAssets() {
        val loaded = ExerciseLoader.loadAll(requireContext().assets)
        updateExercisesList(loaded)
        Log.d(TAG, "Loaded ${loaded.size} exercises from assets (fallback)")
    }

    private fun updateExercisesList(loaded: List<ExerciseConfig>) {
        exercises.clear()
        exercises.addAll(loaded)
        filterExercises()
    }

    private fun filterExercises() {
        val searchQuery = binding.etSearch.text?.toString()?.lowercase() ?: ""
        
        filteredExercises.clear()
        filteredExercises.addAll(exercises.filter { exercise ->
            val matchesCategory = currentCategory == null || 
                exercise.category.name.en.lowercase().contains(currentCategory!!) ||
                exercise.category.name.ar.lowercase().contains(currentCategory!!) ||
                exercise.muscles.any { it.lowercase().contains(currentCategory!!) }
            
            val matchesSearch = searchQuery.isEmpty() ||
                exercise.name.en.lowercase().contains(searchQuery) ||
                exercise.name.ar.contains(searchQuery)
            
            matchesCategory && matchesSearch
        })
        
        binding.rvExercises.adapter?.notifyDataSetChanged()
        
        if (filteredExercises.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
        } else {
            binding.layoutEmpty.visibility = View.GONE
        }
    }

    private fun openExerciseDetail(exercise: ExerciseConfig) {
        val intent = Intent(requireContext(), PreWorkoutActivity::class.java).apply {
            putExtra(PreWorkoutActivity.EXTRA_EXERCISE_NAME, exercise.fileName)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Adapter for exercise cards
     */
    inner class ExerciseAdapter(
        private val items: List<ExerciseConfig>,
        private val onClick: (ExerciseConfig) -> Unit
    ) : RecyclerView.Adapter<ExerciseAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: CardView = view.findViewById(R.id.cardExercise)
            val tvName: TextView = view.findViewById(R.id.tvExerciseName)
            val tvCategory: TextView = view.findViewById(R.id.tvCategory)
            val tvDifficulty: TextView = view.findViewById(R.id.tvDifficulty)
            val ivBookmark: ImageView? = view.findViewById(R.id.ivBookmark)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_exercise_card_new, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val exercise = items[position]
            val language = getCurrentLanguage()
            
            holder.tvName.text = exercise.name.get(language).ifBlank { exercise.name.en }
            holder.tvCategory.text = exercise.category.name.get(language).ifBlank { exercise.category.name.en }
            
            // Difficulty indicator (mock for now)
            val difficulty = when {
                exercise.muscles.size > 2 -> getString(R.string.hard)
                exercise.muscles.size > 1 -> getString(R.string.medium)
                else -> getString(R.string.easy)
            }
            holder.tvDifficulty.text = difficulty
            
            holder.card.setOnClickListener {
                onClick(exercise)
            }
        }

        override fun getItemCount() = items.size
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
}
