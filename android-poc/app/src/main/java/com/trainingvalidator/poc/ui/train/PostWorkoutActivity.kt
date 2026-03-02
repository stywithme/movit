package com.trainingvalidator.poc.ui.train

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.tabs.TabLayout
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityPostWorkoutBinding

/**
 * PostWorkoutActivity - Post-workout report screen
 * 
 * Shows:
 * - Workout completion message (Excellent!, Good job!, etc.)
 * - Summary stats (Reps, Accuracy, Duration)
 * - Tabbed content (Best, Errors, Stats, Tips)
 * - Error details with fix suggestions
 * - Save & Finish action
 */
class PostWorkoutActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "PostWorkoutActivity"
        
        // Intent extras
        const val EXTRA_EXERCISE_NAME = "exercise_name"
        const val EXTRA_TOTAL_REPS = "total_reps"
        const val EXTRA_CORRECT_REPS = "correct_reps"
        const val EXTRA_ACCURACY = "accuracy"
        const val EXTRA_DURATION_MS = "duration_ms"
        const val EXTRA_ERRORS = "errors"
    }

    private lateinit var binding: ActivityPostWorkoutBinding
    
    // Workout data
    private var exerciseName: String = ""
    private var totalReps: Int = 0
    private var correctReps: Int = 0
    private var accuracy: Float = 0f
    private var durationMs: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPostWorkoutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Load data from intent
        loadWorkoutData()
        
        // Setup UI
        setupToolbar()
        setupHeroSection()
        setupStatsSection()
        setupTabs()
        setupErrorsContent()
        setupActions()
    }

    private fun loadWorkoutData() {
        exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Exercise"
        totalReps = intent.getIntExtra(EXTRA_TOTAL_REPS, 12)
        correctReps = intent.getIntExtra(EXTRA_CORRECT_REPS, 10)
        accuracy = intent.getFloatExtra(EXTRA_ACCURACY, 92f)
        durationMs = intent.getLongExtra(EXTRA_DURATION_MS, 90000L)
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { 
            finish()
        }
    }

    private fun setupHeroSection() {
        // Set rating message based on accuracy
        val (message, color) = when {
            accuracy >= 90 -> getString(R.string.excellent) to R.color.primary
            accuracy >= 75 -> getString(R.string.good) to R.color.success
            accuracy >= 60 -> "Good Effort!" to R.color.warning
            else -> getString(R.string.needs_work) to R.color.error
        }
        
        binding.tvRatingMessage.text = message
        binding.tvRatingMessage.setTextColor(ContextCompat.getColor(this, color))
    }

    private fun setupStatsSection() {
        // Reps
        binding.tvReps.text = totalReps.toString()
        
        // Accuracy
        binding.tvAccuracy.text = "${accuracy.toInt()}%"
        
        // Duration
        binding.tvDuration.text = formatDuration(durationMs)
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            "$minutes:${seconds.toString().padStart(2, '0')}"
        } else {
            "0:${seconds.toString().padStart(2, '0')}"
        }
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showTab(TabType.BEST)
                    1 -> showTab(TabType.ERRORS)
                    2 -> showTab(TabType.STATS)
                    3 -> showTab(TabType.TIPS)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Default to Errors tab (index 1)
        binding.tabLayout.getTabAt(1)?.select()
    }

    private fun showTab(tabType: TabType) {
        binding.bestTabContent.visibility = if (tabType == TabType.BEST) View.VISIBLE else View.GONE
        binding.errorsTabContent.visibility = if (tabType == TabType.ERRORS) View.VISIBLE else View.GONE
        binding.statsTabContent.visibility = if (tabType == TabType.STATS) View.VISIBLE else View.GONE
        binding.tipsTabContent.visibility = if (tabType == TabType.TIPS) View.VISIBLE else View.GONE
    }

    private fun setupErrorsContent() {
        // Set sample error content (would be populated from actual workout data)
        binding.tvErrorTitle.text = "Spine curvature detected"
        binding.tvErrorDescription.text = "Detected consistently during Reps 8-10. This alignment issue increases lower back strain."
        binding.tvReplayLabel.text = "Replay Rep 8"
        
        // Fix suggestion
        binding.tvFixDescription.text = "Keep neck neutral. Imagine holding a tennis ball under your chin to maintain alignment throughout the lift."
        
        // Replay button action
        binding.btnReplayRep.setOnClickListener {
            // TODO: Open video replay for the specific rep
        }
    }

    private fun setupActions() {
        binding.btnSaveFinish.setOnClickListener {
            // Save workout to history
            saveWorkoutToHistory()
            
            // Navigate back to main screen
            finish()
        }
    }

    private fun saveWorkoutToHistory() {
        // TODO: Implement saving workout data to local database/history
        // This would involve:
        // 1. Creating a WorkoutHistory entry
        // 2. Storing rep-by-rep data
        // 3. Storing error snapshots
        // 4. Syncing with backend if available
    }

    /**
     * Tab types for content switching
     */
    private enum class TabType {
        BEST, ERRORS, STATS, TIPS
    }

    /**
     * Error data class
     */
    data class WorkoutError(
        val title: String,
        val description: String,
        val priority: String,
        val repNumber: Int,
        val fixSuggestion: String,
        val snapshotUri: String? = null
    )
}
