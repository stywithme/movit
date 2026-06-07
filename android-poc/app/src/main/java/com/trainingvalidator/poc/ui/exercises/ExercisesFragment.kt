package com.trainingvalidator.poc.ui.exercises

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentExercisesBinding
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.WorkoutRepository
import com.trainingvalidator.poc.storage.SyncManager
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.WorkoutConfig
import com.trainingvalidator.poc.ui.train.PreWorkoutActivity
import com.trainingvalidator.poc.ui.utils.ExerciseSearchMatcher
import com.trainingvalidator.poc.ui.workouts.WorkoutListActivity
import com.trainingvalidator.poc.ui.programs.ProgramListActivity
import com.trainingvalidator.poc.ui.workouts.WorkoutDetailActivity
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
    private val workouts = mutableListOf<WorkoutConfig>()
    
    private val repository: ExerciseRepository by lazy { 
        ExerciseRepository.getInstance(requireContext())
    }

    private val workoutRepository: WorkoutRepository by lazy {
        WorkoutRepository.getInstance(requireContext())
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

        // Setup Workouts list (horizontal)
        binding.rvWorkouts.layoutManager = LinearLayoutManager(
            requireContext(),
            LinearLayoutManager.HORIZONTAL,
            false
        )
        binding.rvWorkouts.adapter = WorkoutAdapter(workouts) { workout ->
            openWorkout(workout)
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

        // Search (live filter while typing)
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = filterExercises()
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        binding.etSearch.setOnEditorActionListener { _, _, _ ->
            filterExercises()
            true
        }

        // View all
        binding.tvViewAll.setOnClickListener {
            binding.chipAll.isChecked = true
        }

        // View all workouts
        binding.tvViewAllWorkouts.setOnClickListener {
            startActivity(Intent(requireContext(), WorkoutListActivity::class.java))
        }

        // Featured program
        binding.btnStartProgram.setOnClickListener {
            startActivity(Intent(requireContext(), ProgramListActivity::class.java))
        }
    }

    private fun initializeRepository() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Offline-first: render cached data immediately, then sync in background.
                repository.initialize(autoSync = false)
                loadExercisesFromRepository()

                workoutRepository.initialize()
                loadWorkoutsFromRepository()

                // Background incremental refresh without blocking UI or showing loaders.
                launch {
                    try {
                        val syncResult = repository.checkForUpdates()
                        if (syncResult is com.trainingvalidator.poc.storage.SyncManager.SyncResult.Success &&
                            (syncResult.exercisesUpdated > 0 || syncResult.workoutsUpdated > 0)
                        ) {
                            loadExercisesFromRepository()
                            workoutRepository.reloadFromCache()
                            loadWorkoutsFromRepository()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Background sync failed, keeping cached content", e)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize repository", e)
                // Show empty state - no fallback to assets
                showNoExercisesAvailable()
                showNoWorkoutsAvailable()
            }
        }
    }

    private fun loadExercisesFromRepository() {
        val loaded = repository.getAllExercises()
        
        if (loaded.isEmpty()) {
            // Show empty state - no fallback to assets
            showNoExercisesAvailable()
        } else {
            updateExercisesList(loaded)
            Log.d(TAG, "Loaded ${loaded.size} exercises from repository")
        }
    }

    private fun showNoExercisesAvailable() {
        Log.w(TAG, "No exercises available - cache empty and sync failed")
        updateExercisesList(emptyList())
    }

    private fun loadWorkoutsFromRepository() {
        val loaded = workoutRepository.getAllWorkouts()

        if (loaded.isEmpty()) {
            showNoWorkoutsAvailable()
        } else {
            workouts.clear()
            workouts.addAll(loaded)
            binding.rvWorkouts.adapter?.notifyDataSetChanged()
            binding.layoutWorkoutsEmpty.visibility = View.GONE
        }
    }

    private fun showNoWorkoutsAvailable() {
        Log.w(TAG, "No workouts available - cache empty and sync failed")
        workouts.clear()
        binding.rvWorkouts.adapter?.notifyDataSetChanged()
        binding.layoutWorkoutsEmpty.visibility = View.VISIBLE
    }

    private fun updateExercisesList(loaded: List<ExerciseConfig>) {
        exercises.clear()
        exercises.addAll(loaded)
        filterExercises()
    }

    private fun filterExercises() {
        val searchQuery = binding.etSearch.text?.toString().orEmpty()
        val language = requireContext().currentLanguage

        filteredExercises.clear()
        filteredExercises.addAll(exercises.filter { exercise ->
            val matchesCategory = currentCategory?.let { cat ->
                exercise.category.code.equals(cat, ignoreCase = true) ||
                    exercise.category.name.en.contains(cat, ignoreCase = true) ||
                    exercise.category.name.ar.contains(cat, ignoreCase = true) ||
                    exercise.muscles.any { it.contains(cat, ignoreCase = true) }
            } ?: true

            val matchesSearch = ExerciseSearchMatcher.matches(exercise, searchQuery, language)

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

    private fun openWorkout(workout: WorkoutConfig) {
        val intent = Intent(requireContext(), WorkoutDetailActivity::class.java).apply {
            putExtra(WorkoutDetailActivity.EXTRA_WORKOUT_NAME, workout.fileName)
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
            val language = requireContext().currentLanguage
            
            holder.tvName.text = exercise.name.get(language).ifBlank { exercise.name.en }
            holder.tvCategory.text = exercise.category.name.get(language).ifBlank { exercise.category.name.en }
            
            holder.tvDifficulty.text = exercise.category.name.get(language).ifBlank { exercise.category.code }
            
            holder.card.setOnClickListener {
                onClick(exercise)
            }
        }

        override fun getItemCount() = items.size
    }

    /**
     * Adapter for workout cards (compact)
     */
    inner class WorkoutAdapter(
        private val items: List<WorkoutConfig>,
        private val onClick: (WorkoutConfig) -> Unit
    ) : RecyclerView.Adapter<WorkoutAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: CardView = view.findViewById(R.id.cardWorkout)
            val tvType: TextView = view.findViewById(R.id.tvWorkoutType)
            val tvName: TextView = view.findViewById(R.id.tvWorkoutName)
            val tvDescription: TextView = view.findViewById(R.id.tvWorkoutDescription)
            val tvExerciseCount: TextView = view.findViewById(R.id.tvExerciseCount)
            val tvDuration: TextView = view.findViewById(R.id.tvDuration)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_workout_card_compact, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val workout = items[position]
            val language = requireContext().currentLanguage

            holder.tvName.text = workout.name.get(language).ifBlank { workout.name.en }
            holder.tvDescription.text = workout.description?.let { desc ->
                desc.get(language).ifBlank { desc.en }
            } ?: ""

            holder.tvType.text = formatWorkoutLevel(workout)

            holder.tvExerciseCount.text = getString(
                R.string.exercises_count_format,
                workout.exercises.size
            )

            val durationMinutes = (workout.getEstimatedDurationMs() / 60000).toInt()
            holder.tvDuration.text = getString(R.string.duration_minutes_format, durationMinutes)

            holder.card.setOnClickListener { onClick(workout) }
        }

        override fun getItemCount() = items.size
    }

    private fun formatWorkoutLevel(workout: WorkoutConfig): String {
        val level = workout.level ?: return getString(R.string.workout_detail_default_difficulty)
        val label = level.name.get(requireContext().currentLanguage).ifBlank { level.name.en }.ifBlank { level.code }
        return if (level.number > 0) "Level ${level.number} • $label" else label
    }
}
