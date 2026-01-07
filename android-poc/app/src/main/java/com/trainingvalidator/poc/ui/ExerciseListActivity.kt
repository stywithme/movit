package com.trainingvalidator.poc.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityExerciseListBinding
import com.trainingvalidator.poc.training.loader.ExerciseLoader
import com.trainingvalidator.poc.training.models.ExerciseConfig

/**
 * ExerciseListActivity - Main screen showing available exercises as cards
 * 
 * Flow:
 * 1. User sees all exercises as cards
 * 2. User taps an exercise card
 * 3. Navigate to ExerciseDetailActivity
 */
class ExerciseListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ExerciseListActivity"
    }

    private lateinit var binding: ActivityExerciseListBinding
    private val exercises = mutableListOf<ExerciseConfig>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityExerciseListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        loadExercises()
    }

    private fun setupUI() {
        // Setup RecyclerView with 2-column grid
        binding.rvExercises.layoutManager = GridLayoutManager(this, 2)
        binding.rvExercises.adapter = ExerciseAdapter(exercises) { exercise ->
            openExerciseDetail(exercise)
        }
        
        // Workout Programs button
        binding.btnWorkouts.setOnClickListener {
            openWorkoutList()
        }
    }
    
    private fun openWorkoutList() {
        val intent = Intent(this, WorkoutListActivity::class.java)
        startActivity(intent)
    }

    private fun loadExercises() {
        // Load all exercises from assets
        val loaded = ExerciseLoader.loadAll(assets)
        
        if (loaded.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.rvExercises.visibility = View.GONE
        } else {
            exercises.clear()
            exercises.addAll(loaded)
            binding.rvExercises.adapter?.notifyDataSetChanged()
            binding.tvEmpty.visibility = View.GONE
            binding.rvExercises.visibility = View.VISIBLE
        }
    }

    private fun openExerciseDetail(exercise: ExerciseConfig) {
        val intent = Intent(this, ExerciseDetailActivity::class.java).apply {
            // Use the stored file name from ExerciseLoader
            putExtra(ExerciseDetailActivity.EXTRA_EXERCISE_NAME, exercise.fileName)
        }
        startActivity(intent)
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
            val tvNameAr: TextView = view.findViewById(R.id.tvExerciseNameAr)
            val tvCategory: TextView = view.findViewById(R.id.tvCategory)
            val tvMuscles: TextView = view.findViewById(R.id.tvMuscles)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_exercise_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val exercise = items[position]
            
            holder.tvName.text = exercise.name.en
            holder.tvNameAr.text = exercise.name.ar
            holder.tvCategory.text = exercise.category.name.en
            holder.tvMuscles.text = exercise.muscles.joinToString(", ") { 
                it.replaceFirstChar { c -> c.uppercase() } 
            }
            
            holder.card.setOnClickListener {
                onClick(exercise)
            }
        }

        override fun getItemCount() = items.size
    }
}
