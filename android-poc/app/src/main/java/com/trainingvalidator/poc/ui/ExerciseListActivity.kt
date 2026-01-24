package com.trainingvalidator.poc.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityExerciseListBinding
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.SyncManager
import com.trainingvalidator.poc.training.loader.ExerciseLoader
import com.trainingvalidator.poc.training.models.ExerciseConfig
import kotlinx.coroutines.launch

/**
 * ExerciseListActivity - Main screen showing available exercises as cards
 * 
 * Flow:
 * 1. Load exercises from Repository (with sync support)
 * 2. User sees all exercises as cards
 * 3. User taps an exercise card
 * 4. Navigate to ExerciseDetailActivity
 * 
 * Sync:
 * - Auto-syncs on app start (incremental)
 * - Manual refresh via swipe-to-refresh or button
 */
class ExerciseListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ExerciseListActivity"
    }

    private lateinit var binding: ActivityExerciseListBinding
    private val exercises = mutableListOf<ExerciseConfig>()
    
    // Exercise Repository (singleton)
    private val repository: ExerciseRepository by lazy { 
        ExerciseRepository.getInstance(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        binding = ActivityExerciseListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupUI()
        initializeRepository()
    }
    
    override fun onResume() {
        super.onResume()
        
        // Check for updates when returning to this screen
        // This ensures we catch any updates made while app was in background
        lifecycleScope.launch {
            try {
                val result = repository.checkForUpdates()
                if (result is SyncManager.SyncResult.Success && result.exercisesUpdated > 0) {
                    loadExercisesFromRepository()
                    Log.d(TAG, "Updated ${result.exercisesUpdated} exercises on resume")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Update check on resume failed", e)
            }
        }
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
    
    /**
     * Initialize repository and load exercises with sync
     */
    private fun initializeRepository() {
        lifecycleScope.launch {
            try {
                // Show loading state
                binding.tvEmpty.text = "Loading exercises..."
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvExercises.visibility = View.GONE
                
                // Initialize repository (loads cache + syncs)
                repository.initialize(autoSync = true)
                
                // Load exercises
                loadExercisesFromRepository()
                
                // Log sync result
                repository.lastSyncResult.value?.let { result ->
                    when (result) {
                        is SyncManager.SyncResult.Success -> {
                            Log.d(TAG, "Sync success: ${result.exercisesUpdated} updated, ${result.audioFilesDownloaded} audio files")
                            if (result.exercisesUpdated > 0) {
                                Toast.makeText(this@ExerciseListActivity, 
                                    "Updated ${result.exercisesUpdated} exercises", 
                                    Toast.LENGTH_SHORT).show()
                            }
                        }
                        is SyncManager.SyncResult.Offline -> {
                            Log.d(TAG, "Offline - using cached data")
                        }
                        is SyncManager.SyncResult.Error -> {
                            Log.e(TAG, "Sync error: ${result.message}")
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize repository", e)
                // Fall back to asset loading
                loadExercisesFromAssets()
            }
        }
    }
    
    /**
     * Load exercises from repository (cached/synced data)
     */
    private fun loadExercisesFromRepository() {
        val loaded = repository.getAllExercises()
        
        if (loaded.isEmpty()) {
            // Fall back to assets if repository is empty
            loadExercisesFromAssets()
        } else {
            updateExercisesList(loaded)
            Log.d(TAG, "Loaded ${loaded.size} exercises from repository")
        }
    }

    /**
     * Fallback: Load exercises from bundled assets
     */
    private fun loadExercisesFromAssets() {
        val loaded = ExerciseLoader.loadAll(assets)
        updateExercisesList(loaded)
        Log.d(TAG, "Loaded ${loaded.size} exercises from assets (fallback)")
    }
    
    /**
     * Update the exercises list and refresh UI
     */
    private fun updateExercisesList(loaded: List<ExerciseConfig>) {
        if (loaded.isEmpty()) {
            binding.tvEmpty.text = "No exercises available"
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvExercises.visibility = View.GONE
        } else {
            exercises.clear()
            exercises.addAll(loaded)
            binding.rvExercises.adapter?.notifyDataSetChanged()
            binding.layoutEmpty.visibility = View.GONE
            binding.rvExercises.visibility = View.VISIBLE
        }
    }
    
    /**
     * Manual refresh (can be called from menu or button)
     */
    fun refreshExercises() {
        lifecycleScope.launch {
            try {
                val result = repository.refresh()
                
                when (result) {
                    is SyncManager.SyncResult.Success -> {
                        loadExercisesFromRepository()
                        Toast.makeText(this@ExerciseListActivity, 
                            "Refreshed ${result.exercisesUpdated} exercises", 
                            Toast.LENGTH_SHORT).show()
                    }
                    is SyncManager.SyncResult.Offline -> {
                        Toast.makeText(this@ExerciseListActivity, 
                            "Offline - cannot refresh", 
                            Toast.LENGTH_SHORT).show()
                    }
                    is SyncManager.SyncResult.Error -> {
                        Toast.makeText(this@ExerciseListActivity, 
                            "Refresh failed: ${result.message}", 
                            Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "Refresh failed", e)
                Toast.makeText(this@ExerciseListActivity, 
                    "Refresh failed", 
                    Toast.LENGTH_SHORT).show()
            }
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
