package com.trainingvalidator.poc.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityWorkoutDetailBinding
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.WorkoutRepository
import com.trainingvalidator.poc.training.models.*

/**
 * WorkoutDetailActivity - Shows workout overview with timeline before starting
 *
 * Displays:
 * - Workout hero header (name, difficulty, description)
 * - Stats row (exercises count, duration, total sets)
 * - Timeline of exercises with rest periods between them
 * - Start button
 *
 * Navigation:
 * - ExercisesFragment / WorkoutListActivity → WorkoutDetailActivity → WorkoutActivity
 */
class WorkoutDetailActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WorkoutDetailActivity"
        const val EXTRA_WORKOUT_NAME = "workout_name"

        private const val TYPE_ROUND_HEADER = 0
        private const val TYPE_EXERCISE = 1
        private const val TYPE_REST = 2
    }

    private lateinit var binding: ActivityWorkoutDetailBinding
    private var workoutConfig: WorkoutConfig? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityWorkoutDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val workoutName = intent.getStringExtra(EXTRA_WORKOUT_NAME)
        if (workoutName == null) {
            Toast.makeText(this, getString(R.string.no_workout_specified), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        loadWorkout(workoutName)
        setupUI()
    }

    private fun loadWorkout(name: String) {
        workoutConfig = try {
            val repository = WorkoutRepository.getInstance(this)
            repository.getWorkout(name)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load workout: $name", e)
            null
        }

        if (workoutConfig == null) {
            Toast.makeText(this, getString(R.string.failed_to_load_workout_format, name), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupUI() {
        val config = workoutConfig ?: return
        val language = getCurrentLanguage()

        // Toolbar
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Workout name & description
        binding.tvWorkoutName.text = config.name.get(language).ifBlank { config.name.en }
        binding.tvWorkoutDescription.text = config.description?.let { desc ->
            desc.get(language).ifBlank { desc.en }
        } ?: ""

        // Difficulty badge
        binding.tvWorkoutTypeBadge.text = formatDifficulty(config.difficulty)

        // Stats
        binding.tvStatExercises.text = config.exercises.size.toString()
        val durationMinutes = (config.getEstimatedDurationMs() / 60000).toInt()
        binding.tvStatDuration.text = getString(R.string.duration_minutes_format, durationMinutes)
        binding.tvStatRounds.text = config.exercises.sumOf { it.sets }.toString()

        // Execution mode info
        setupExecutionModeInfo()

        // Timeline
        setupTimeline(config)

        // Start button
        binding.btnStartWorkout.setOnClickListener {
            startWorkout()
        }
    }

    private fun setupExecutionModeInfo() {
        binding.cardExecutionMode.visibility = View.GONE
    }

    private fun setupTimeline(config: WorkoutConfig) {
        val timelineItems = buildTimelineItems(config)

        binding.rvTimeline.layoutManager = LinearLayoutManager(this)
        binding.rvTimeline.adapter = TimelineAdapter(timelineItems)
    }

    /**
     * Build the timeline items list for workout templates.
     * Exercise → Rest → Exercise → Rest → ...
     */
    private fun buildTimelineItems(config: WorkoutConfig): List<TimelineItem> {
        val items = mutableListOf<TimelineItem>()
        val exerciseRepo = ExerciseRepository.getInstance(this)
        val language = getCurrentLanguage()

        config.exercises.forEachIndexed { index, workoutExercise ->
            val exerciseConfig = exerciseRepo.getExercise(workoutExercise.exercise)
            val exerciseName = exerciseConfig?.name?.get(language)?.ifBlank {
                exerciseConfig.name.en
            } ?: workoutExercise.exercise

            val targetText = when {
                workoutExercise.targetDurationSec != null -> {
                    getString(
                        R.string.workout_detail_sets_hold_format,
                        workoutExercise.sets,
                        workoutExercise.targetDurationSec
                    )
                }
                workoutExercise.targetReps != null -> {
                    getString(
                        R.string.workout_detail_sets_reps_format,
                        workoutExercise.sets,
                        workoutExercise.targetReps
                    )
                }
                else -> {
                    val fallback = exerciseConfig?.let { cfg ->
                        when (cfg.countingMethod) {
                            CountingMethod.HOLD -> getString(R.string.workout_detail_hold_exercise)
                            else -> getString(R.string.workout_detail_reps_exercise)
                        }
                    } ?: ""
                    getString(R.string.workout_detail_sets_generic_format, workoutExercise.sets, fallback)
                }
            }

            items.add(
                TimelineItem.Exercise(
                    number = index + 1,
                    name = exerciseName,
                    target = targetText,
                    exerciseSlug = workoutExercise.exercise,
                    isFirst = items.isEmpty(),
                    isLast = index == config.exercises.size - 1
                )
            )

            if (index < config.exercises.size - 1) {
                val restSeconds = workoutExercise.restAfterExerciseMs / 1000
                if (restSeconds > 0) {
                    items.add(
                        TimelineItem.Rest(
                            durationSeconds = restSeconds,
                            isRoundRest = false
                        )
                    )
                }
            }
        }

        return items
    }

    private fun formatDifficulty(difficulty: String): String {
        if (difficulty.isBlank()) return getString(R.string.workout_detail_default_difficulty)
        val normalized = difficulty.replace('_', ' ').lowercase()
        return normalized.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun startWorkout() {
        val config = workoutConfig ?: return

        val intent = Intent(this, WorkoutActivity::class.java).apply {
            putExtra(WorkoutActivity.EXTRA_WORKOUT_NAME, config.fileName)
        }
        startActivity(intent)
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

    // ==================== Timeline Data Models ====================

    sealed class TimelineItem {
        data class RoundHeader(
            val round: Int,
            val isFirst: Boolean
        ) : TimelineItem()

        data class Exercise(
            val number: Int,
            val name: String,
            val target: String,
            val exerciseSlug: String,
            val isFirst: Boolean,
            val isLast: Boolean
        ) : TimelineItem()

        data class Rest(
            val durationSeconds: Long,
            val isRoundRest: Boolean
        ) : TimelineItem()
    }

    // ==================== Timeline Adapter ====================

    inner class TimelineAdapter(
        private val items: List<TimelineItem>
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemViewType(position: Int): Int = when (items[position]) {
            is TimelineItem.RoundHeader -> TYPE_ROUND_HEADER
            is TimelineItem.Exercise -> TYPE_EXERCISE
            is TimelineItem.Rest -> TYPE_REST
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                TYPE_ROUND_HEADER -> RoundHeaderVH(
                    inflater.inflate(R.layout.item_timeline_round_header, parent, false)
                )
                TYPE_EXERCISE -> ExerciseVH(
                    inflater.inflate(R.layout.item_timeline_exercise, parent, false)
                )
                TYPE_REST -> RestVH(
                    inflater.inflate(R.layout.item_timeline_rest, parent, false)
                )
                else -> throw IllegalArgumentException("Unknown view type: $viewType")
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is TimelineItem.RoundHeader -> (holder as RoundHeaderVH).bind(item)
                is TimelineItem.Exercise -> (holder as ExerciseVH).bind(item)
                is TimelineItem.Rest -> (holder as RestVH).bind(item)
            }
        }

        override fun getItemCount() = items.size

        // ========== ViewHolders ==========

        inner class RoundHeaderVH(view: View) : RecyclerView.ViewHolder(view) {
            private val tvRoundLabel: TextView = view.findViewById(R.id.tvRoundLabel)
            private val lineTop: View = view.findViewById(R.id.lineTop)

            fun bind(item: TimelineItem.RoundHeader) {
                tvRoundLabel.text = getString(R.string.workout_detail_round_format, item.round)
                // Hide line for the very first item
                lineTop.visibility = if (item.isFirst) View.INVISIBLE else View.VISIBLE
            }
        }

        inner class ExerciseVH(view: View) : RecyclerView.ViewHolder(view) {
            private val tvNumber: TextView = view.findViewById(R.id.tvExerciseNumber)
            private val tvName: TextView = view.findViewById(R.id.tvExerciseName)
            private val tvTarget: TextView = view.findViewById(R.id.tvExerciseTarget)
            private val lineTop: View = view.findViewById(R.id.lineTop)
            private val dotIndicator: View = view.findViewById(R.id.dotIndicator)

            fun bind(item: TimelineItem.Exercise) {
                tvNumber.text = item.number.toString()
                tvName.text = item.name
                tvTarget.text = item.target

                // Hide top line for the very first item in the timeline
                lineTop.visibility = if (item.isFirst) View.INVISIBLE else View.VISIBLE
            }
        }

        inner class RestVH(view: View) : RecyclerView.ViewHolder(view) {
            private val tvRestDuration: TextView = view.findViewById(R.id.tvRestDuration)

            fun bind(item: TimelineItem.Rest) {
                val label = if (item.isRoundRest) {
                    getString(R.string.workout_detail_round_rest_format, item.durationSeconds)
                } else {
                    getString(R.string.workout_detail_rest_format, item.durationSeconds)
                }
                tvRestDuration.text = label
            }
        }
    }
}
