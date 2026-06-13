package com.trainingvalidator.poc.ui.workouts

import android.graphics.drawable.Animatable
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.trainingvalidator.poc.storage.EntityAudioPrefetchManager
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.android.material.chip.Chip
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityWorkoutDetailBinding
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.WorkoutRepository
import com.google.gson.Gson
import com.movit.navigation.MovitTrainingEntryNavigator
import com.trainingvalidator.poc.training.models.*

/**
 * WorkoutDetailActivity - Shows workout overview with timeline before starting
 *
 * Displays:
 * - Workout hero header (cover image, name, level, quick summary)
 * - Basic info sections (stats, tags, description)
 * - Timeline of exercises with rest periods between them
 * - Start button
 *
 * Navigation:
 * - ExercisesFragment / WorkoutListActivity → WorkoutDetailActivity (template preview)
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
        if (workoutConfig == null) return

        setupUI()

        lifecycleScope.launch {
            val cfg = workoutConfig ?: return@launch
            if (cfg.fileName.isBlank()) return@launch
            EntityAudioPrefetchManager(this@WorkoutDetailActivity).prefetchWorkoutIfNeeded(cfg.fileName, cfg)
        }
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
        val language = currentLanguage
        val totalSets = config.exercises.sumOf { it.sets }

        // Toolbar
        binding.toolbar.setNavigationOnClickListener { finish() }

        // Workout identity
        binding.tvWorkoutName.text = config.name.get(language).ifBlank { config.name.en }
        binding.tvWorkoutDescription.text = getString(
            R.string.workout_customize_summary_format,
            config.exercises.size,
            totalSets,
            resolvedDurationMinutes(config)
        )

        binding.tvWorkoutTypeBadge.text = formatWorkoutLevel(config)

        // Media & supporting info
        setupPreviewImage()
        setupTags()
        setupDescription()

        // Stats
        binding.tvStatExercises.text = config.exercises.size.toString()
        binding.tvStatDuration.text = getString(
            R.string.duration_minutes_format,
            resolvedDurationMinutes(config)
        )
        binding.tvStatRounds.text = totalSets.toString()

        // Execution mode info
        setupExecutionModeInfo()

        // Timeline
        setupTimeline(config)

        // Start workout — launches WorkoutRunActivity for full multi-exercise training
        binding.btnStartWorkout.text = getString(R.string.start_workout)
        binding.btnStartWorkout.setOnClickListener {
            launchWorkoutRun(config)
        }

        // Customize button — launches WorkoutCustomizeActivity to reorder/edit before starting
        binding.btnCustomizeWorkout.visibility = View.VISIBLE
        binding.btnCustomizeWorkout.setOnClickListener {
            val intent = WorkoutCustomizeActivity.createIntent(this, config)
            startActivity(intent)
        }
    }

    private fun setupPreviewImage() {
        val imageUrl = workoutConfig?.coverImageUrl
        binding.badgeLoopingPreview.visibility = View.GONE

        if (imageUrl.isNullOrBlank()) {
            binding.ivWorkoutPreview.setImageDrawable(null)
            binding.ivWorkoutFallbackIcon.visibility = View.VISIBLE
            return
        }

        binding.ivWorkoutPreview.load(imageUrl) {
            placeholder(R.drawable.gradient_report_hero)
            error(R.drawable.gradient_report_hero)
            crossfade(true)
            listener(
                onStart = { _ ->
                    binding.ivWorkoutFallbackIcon.visibility = View.GONE
                    binding.badgeLoopingPreview.visibility = View.GONE
                },
                onError = { _, _ ->
                    binding.ivWorkoutFallbackIcon.visibility = View.VISIBLE
                    binding.badgeLoopingPreview.visibility = View.GONE
                },
                onSuccess = { _, result ->
                    binding.ivWorkoutFallbackIcon.visibility = View.GONE
                    binding.badgeLoopingPreview.visibility =
                        if (result.drawable is Animatable) View.VISIBLE else View.GONE
                }
            )
        }
    }

    private fun setupTags() {
        val tags = resolveWorkoutTags()
        binding.chipGroupWorkoutTags.removeAllViews()

        if (tags.isEmpty()) {
            binding.tagsSection.visibility = View.GONE
            return
        }

        binding.tagsSection.visibility = View.VISIBLE
        tags.forEach { label ->
            val chipContext = ContextThemeWrapper(this, R.style.Widget_WayToFix_Chip_Filter)
            val chip = Chip(chipContext, null).apply {
                text = label
                isCheckable = false
                isClickable = false
            }
            binding.chipGroupWorkoutTags.addView(chip)
        }
    }

    private fun resolveWorkoutTags(): List<String> {
        val config = workoutConfig ?: return emptyList()
        val language = currentLanguage
        val exerciseRepo = ExerciseRepository.getInstance(this)
        val resolvedTags = config.tags
            .map(::humanizeCode)
            .filter { it.isNotBlank() }
            .distinct()
            .toMutableList()

        if (resolvedTags.isEmpty()) {
            resolvedTags += config.exercises
                .mapNotNull { exerciseRepo.getExercise(it.exercise) }
                .map { exercise ->
                    exercise.category.name.get(language)
                        .ifBlank { exercise.category.name.en }
                        .ifBlank { humanizeCode(exercise.category.code) }
                }
                .filter { it.isNotBlank() }
                .distinct()
                .take(3)
        }

        if (resolvedTags.isEmpty()) {
            resolvedTags += formatWorkoutLevel(config)
        }

        return resolvedTags.distinct().take(4)
    }

    private fun setupDescription() {
        val config = workoutConfig ?: return
        val language = currentLanguage
        val descriptionText = config.description?.let { desc ->
            desc.get(language).ifBlank { desc.en }
        }.orEmpty()

        if (descriptionText.isBlank()) {
            binding.descriptionSection.visibility = View.GONE
            return
        }

        binding.descriptionSection.visibility = View.VISIBLE
        binding.tvWorkoutDescriptionValue.text = descriptionText
    }

    private fun resolvedDurationMinutes(config: WorkoutConfig): Int {
        val minutes = config.estimatedDurationMin ?: (config.getEstimatedDurationMs() / 60000L).toInt()
        return minutes.coerceAtLeast(1)
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
        val language = currentLanguage

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

    private fun launchWorkoutRun(config: WorkoutConfig) {
        val workoutId = config.fileName.ifBlank { config.name.en.ifBlank { "local-workout" } }
        MovitTrainingEntryNavigator.openWorkoutSessionWithLocalConfig(
            context = this,
            workoutId = workoutId,
            workoutConfigJson = Gson().toJson(config),
        )
    }

    private fun formatWorkoutLevel(config: WorkoutConfig): String {
        val level = config.level ?: return getString(R.string.workout_detail_default_difficulty)
        val label = level.name.get(currentLanguage).ifBlank { level.name.en }.ifBlank { level.code }
        return if (level.number > 0) "Level ${level.number} • $label" else label
    }

    private fun humanizeCode(code: String): String {
        return code
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
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
