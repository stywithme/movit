package com.trainingvalidator.poc.ui.workouts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityWorkoutCustomizeBinding
import com.trainingvalidator.poc.databinding.ItemWorkoutCustomizeExerciseBinding
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.training.models.WorkoutConfig
import com.trainingvalidator.poc.training.models.WorkoutExercise
import com.trainingvalidator.poc.ui.utils.currentLanguage
import java.util.Collections

/**
 * WorkoutCustomizeActivity — Pre-Training Customization
 *
 * Allows the user to customize a workout before starting:
 * - Drag to reorder exercises
 * - Edit sets, reps, rest duration per exercise
 * - Remove exercises from the session
 *
 * After customization, launches [WorkoutSessionActivity] with the modified config.
 */
class WorkoutCustomizeActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_WORKOUT_CONFIG_JSON = "workout_config_json"

        fun createIntent(context: Context, config: WorkoutConfig): Intent =
            Intent(context, WorkoutCustomizeActivity::class.java).apply {
                putExtra(EXTRA_WORKOUT_CONFIG_JSON, Gson().toJson(config))
            }
    }

    private lateinit var binding: ActivityWorkoutCustomizeBinding
    private lateinit var originalConfig: WorkoutConfig

    /** Mutable copy of exercises — this is the source of truth for the UI */
    private val exercises = mutableListOf<WorkoutExercise>()

    private lateinit var adapter: CustomizeAdapter
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkoutCustomizeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val json = intent.getStringExtra(EXTRA_WORKOUT_CONFIG_JSON)
        originalConfig = if (!json.isNullOrBlank()) {
            gson.fromJson(json, WorkoutConfig::class.java)
        } else {
            finish()
            return
        }

        exercises.addAll(originalConfig.exercises)
        setupToolbar()
        setupRecycler()
        setupStartButton()
        updateSummary()
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        val language = currentLanguage
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.tvTitle.text = originalConfig.name.get(language).ifBlank { originalConfig.name.en }
    }

    private fun setupRecycler() {
        adapter = CustomizeAdapter()
        binding.rvExercises.layoutManager = LinearLayoutManager(this)
        binding.rvExercises.adapter = adapter

        // Drag-to-reorder
        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
                val fromPos = from.bindingAdapterPosition
                val toPos = to.bindingAdapterPosition
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) return false
                Collections.swap(exercises, fromPos, toPos)
                adapter.notifyItemMoved(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        touchHelper.attachToRecyclerView(binding.rvExercises)
    }

    private fun setupStartButton() {
        binding.btnStart.setOnClickListener {
            if (exercises.isEmpty()) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.workout_customize_empty_error))
                    .setPositiveButton(getString(R.string.done)) { d, _ -> d.dismiss() }
                    .show()
                return@setOnClickListener
            }

            val customizedConfig = originalConfig.copy(exercises = exercises.toList())
            startActivity(
                WorkoutSessionActivity.createIntent(
                    context = this,
                    workoutConfig = customizedConfig,
                    workoutId = null,
                    sessionContext = "explore_workout"
                )
            )
        }
    }

    private fun updateSummary() {
        val totalSets = exercises.sumOf { it.sets }
        val estimatedMin = exercises.sumOf { ex ->
            val setTime = (ex.targetReps ?: 10) * 3 + (ex.restBetweenSetsMs / 1000).toInt()
            (setTime * ex.sets) / 60 + (ex.restAfterExerciseMs / 1000 / 60).toInt()
        }
        binding.tvSummary.text = getString(
            R.string.workout_customize_summary_format,
            exercises.size,
            totalSets,
            estimatedMin.coerceAtLeast(1)
        )
    }

    // ── Adapter ────────────────────────────────────────────────────────────────

    inner class CustomizeAdapter : RecyclerView.Adapter<CustomizeAdapter.VH>() {

        inner class VH(val binding: ItemWorkoutCustomizeExerciseBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            ItemWorkoutCustomizeExerciseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

        override fun getItemCount() = exercises.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val exercise = exercises[position]
            val language = currentLanguage
            val exerciseRepo = ExerciseRepository.getInstance(this@WorkoutCustomizeActivity)
            val exerciseConfig = exerciseRepo.getExercise(exercise.exercise)

            val name = exerciseConfig?.name?.get(language)?.ifBlank { exerciseConfig.name.en }
                ?: exercise.exercise.replace('_', ' ').replaceFirstChar { it.uppercase() }

            holder.binding.tvExerciseName.text = name
            holder.binding.tvSets.text = "${exercise.sets}"
            holder.binding.tvReps.text = exercise.targetReps?.let { "$it" } ?: "--"

            val restSec = exercise.restAfterExerciseMs / 1000
            holder.binding.tvRest.text = if (restSec >= 60) {
                "${restSec / 60}m ${restSec % 60}s"
            } else {
                "${restSec}s"
            }

            holder.binding.tvExerciseNumber.text = "${position + 1}"

            // Edit sets
            holder.binding.tvSets.setOnClickListener {
                showEditDialog(
                    title = getString(R.string.workout_customize_edit_sets),
                    current = exercise.sets,
                    min = 1,
                    max = 10
                ) { newValue ->
                    val pos = holder.bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@showEditDialog
                    exercises[pos] = exercise.copy(sets = newValue)
                    notifyItemChanged(pos)
                    updateSummary()
                }
            }

            // Edit reps
            holder.binding.tvReps.setOnClickListener {
                showEditDialog(
                    title = getString(R.string.workout_customize_edit_reps),
                    current = exercise.targetReps ?: 10,
                    min = 1,
                    max = 100
                ) { newValue ->
                    val pos = holder.bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@showEditDialog
                    exercises[pos] = exercise.copy(targetReps = newValue)
                    notifyItemChanged(pos)
                    updateSummary()
                }
            }

            // Edit rest
            holder.binding.tvRest.setOnClickListener {
                showEditDialog(
                    title = getString(R.string.workout_customize_edit_rest),
                    current = (exercise.restAfterExerciseMs / 1000).toInt(),
                    min = 0,
                    max = 300
                ) { newValue ->
                    val pos = holder.bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@showEditDialog
                    exercises[pos] = exercise.copy(restAfterExerciseMs = newValue * 1000L)
                    notifyItemChanged(pos)
                    updateSummary()
                }
            }

            // Remove exercise
            holder.binding.btnRemove.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                exercises.removeAt(pos)
                notifyItemRemoved(pos)
                notifyItemRangeChanged(pos, exercises.size)
                updateSummary()
            }
        }

        private fun showEditDialog(title: String, current: Int, min: Int, max: Int, onConfirm: (Int) -> Unit) {
            val values = (min..max).toList()
            val currentIndex = values.indexOf(current).coerceAtLeast(0)
            val displayValues = values.map { it.toString() }.toTypedArray()

            var selected = current

            MaterialAlertDialogBuilder(this@WorkoutCustomizeActivity)
                .setTitle(title)
                .setSingleChoiceItems(displayValues, currentIndex) { _, which ->
                    selected = values[which]
                }
                .setPositiveButton(getString(R.string.done)) { _, _ -> onConfirm(selected) }
                .setNegativeButton(getString(R.string.cancel)) { d, _ -> d.dismiss() }
                .show()
        }
    }
}
