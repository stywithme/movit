package com.trainingvalidator.poc.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityWorkoutListBinding
import com.trainingvalidator.poc.storage.WorkoutRepository
import com.trainingvalidator.poc.training.loader.WorkoutLoader
import com.trainingvalidator.poc.training.models.WorkoutConfig
import com.trainingvalidator.poc.training.models.WorkoutType
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
        val intent = Intent(this, WorkoutActivity::class.java).apply {
            putExtra(WorkoutActivity.EXTRA_WORKOUT_NAME, workout.fileName)
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
            val card: CardView = view.findViewById(R.id.cardWorkout)
            val tvIcon: TextView = view.findViewById(R.id.tvIcon)
            val tvName: TextView = view.findViewById(R.id.tvWorkoutName)
            val tvNameAr: TextView = view.findViewById(R.id.tvWorkoutNameAr)
            val tvDescription: TextView = view.findViewById(R.id.tvDescription)
            val tvExerciseCount: TextView = view.findViewById(R.id.tvExerciseCount)
            val tvRounds: TextView = view.findViewById(R.id.tvRounds)
            val tvDuration: TextView = view.findViewById(R.id.tvDuration)
            val tvWorkoutType: TextView = view.findViewById(R.id.tvWorkoutType)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_workout_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val workout = items[position]
            
            holder.tvName.text = workout.name.en
            holder.tvNameAr.text = workout.name.ar
            holder.tvDescription.text = workout.description?.en ?: ""
            holder.tvExerciseCount.text = workout.exercises.size.toString()
            holder.tvRounds.text = workout.rounds.toString()
            
            // Calculate estimated duration in minutes
            val durationMinutes = (workout.getEstimatedDurationMs() / 60000).toInt()
            holder.tvDuration.text = "~$durationMinutes"
            
            // Workout type badge
            holder.tvWorkoutType.text = when (workout.type) {
                WorkoutType.CIRCUIT -> "CIRCUIT"
                WorkoutType.SUPER_SET -> "SUPER SET"
                WorkoutType.AMRAP -> "AMRAP"
                WorkoutType.EMOM -> "EMOM"
            }
            
            // Icon based on type
            holder.tvIcon.text = when (workout.type) {
                WorkoutType.CIRCUIT -> "🔥"
                WorkoutType.SUPER_SET -> "💪"
                WorkoutType.AMRAP -> "⏱️"
                WorkoutType.EMOM -> "🎯"
            }
            
            holder.card.setOnClickListener {
                onClick(workout)
            }
        }

        override fun getItemCount() = items.size
    }
}
