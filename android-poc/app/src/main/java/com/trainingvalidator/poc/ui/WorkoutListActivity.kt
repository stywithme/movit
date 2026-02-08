package com.trainingvalidator.poc.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityWorkoutListBinding
import com.trainingvalidator.poc.storage.WorkoutRepository
import com.trainingvalidator.poc.training.loader.WorkoutLoader
import com.trainingvalidator.poc.training.models.WorkoutConfig
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * WorkoutListActivity - Shows available workout programs
 * 
 * Flow:
 * 1. User sees all workouts as cards
 * 2. User taps a workout card
 * 3. Navigate to WorkoutActivity (or WorkoutDetailActivity)
 */
class WorkoutListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WorkoutListActivity"
    }

    private lateinit var binding: ActivityWorkoutListBinding
    private val workouts = mutableListOf<WorkoutConfig>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityWorkoutListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        loadWorkouts()
    }

    private fun setupUI() {
        // Back button
        binding.btnBack.setOnClickListener { finish() }
        
        // Setup RecyclerView with linear layout (full width cards)
        binding.rvWorkouts.layoutManager = LinearLayoutManager(this)
        binding.rvWorkouts.adapter = WorkoutAdapter(workouts) { workout ->
            openWorkout(workout)
        }
    }

    private fun loadWorkouts() {
        // Load workouts from repository (cached from server or assets)
        CoroutineScope(Dispatchers.Main).launch {
            val repository = WorkoutRepository.getInstance(this@WorkoutListActivity)
            
            // Initialize repository if needed (loads from cache or assets)
            withContext(Dispatchers.IO) {
                repository.initialize()
            }
            
            val loaded = repository.getAllWorkouts()
            
            if (loaded.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvWorkouts.visibility = View.GONE
            } else {
                workouts.clear()
                workouts.addAll(loaded)
                binding.rvWorkouts.adapter?.notifyDataSetChanged()
                binding.layoutEmpty.visibility = View.GONE
                binding.rvWorkouts.visibility = View.VISIBLE
            }
        }
    }

    private fun openWorkout(workout: WorkoutConfig) {
        val intent = Intent(this, WorkoutDetailActivity::class.java).apply {
            putExtra(WorkoutDetailActivity.EXTRA_WORKOUT_NAME, workout.fileName)
        }
        startActivity(intent)
    }

    /**
     * Adapter for workout cards
     */
    inner class WorkoutAdapter(
        private val items: List<WorkoutConfig>,
        private val onClick: (WorkoutConfig) -> Unit
    ) : RecyclerView.Adapter<WorkoutAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivWorkoutImage: ImageView = view.findViewById(R.id.ivWorkoutImage)
            val tvName: TextView = view.findViewById(R.id.tvWorkoutName)
            val tvDescription: TextView = view.findViewById(R.id.tvDescription)
            val tvExerciseCount: TextView = view.findViewById(R.id.tvExerciseCount)
            val tvDuration: TextView = view.findViewById(R.id.tvDuration)
            val tvDifficulty: TextView = view.findViewById(R.id.tvDifficulty)
            val tvMuscles: TextView = view.findViewById(R.id.tvMuscles)
            val btnStart: View = view.findViewById(R.id.btnStart)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_workout_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val workout = items[position]
            val language = getCurrentLanguage()
            
            holder.tvName.text = workout.name.get(language).ifBlank { workout.name.en }
            holder.tvDescription.text = workout.description?.let { desc ->
                desc.get(language).ifBlank { desc.en }
            } ?: ""
            holder.tvExerciseCount.text = getString(
                R.string.exercises_count_format,
                workout.exercises.size
            )
            
            // Calculate estimated duration in minutes
            val durationMinutes = (workout.getEstimatedDurationMs() / 60000).toInt()
            holder.tvDuration.text = getString(R.string.duration_minutes_format, durationMinutes)
            
            // Workout difficulty badge
            holder.tvDifficulty.text = formatDifficulty(workout.difficulty)

            // Optional: muscles not available in workout config
            holder.tvMuscles.visibility = View.GONE

            holder.itemView.setOnClickListener { onClick(workout) }
            holder.btnStart.setOnClickListener { onClick(workout) }
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

    private fun formatDifficulty(difficulty: String): String {
        if (difficulty.isBlank()) return getString(R.string.workout_detail_default_difficulty)
        val normalized = difficulty.replace('_', ' ').lowercase()
        return normalized.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
