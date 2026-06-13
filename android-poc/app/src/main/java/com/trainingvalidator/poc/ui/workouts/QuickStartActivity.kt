package com.trainingvalidator.poc.ui.workouts

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.movit.navigation.MovitTrainingEntryNavigator
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityQuickStartBinding
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.LocalizedText
import com.trainingvalidator.poc.training.models.WorkoutConfig
import com.trainingvalidator.poc.training.models.WorkoutExecutionContext
import com.trainingvalidator.poc.training.models.WorkoutExercise
import com.trainingvalidator.poc.ui.utils.ExerciseSearchMatcher
import com.trainingvalidator.poc.ui.utils.currentLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * QuickStartActivity — Build a free workout on-the-fly
 *
 * Flow:
 *   ExploreFragment → QuickStartActivity
 *     1. Browse & multi-select exercises from the library
 *     2. Reorder selected exercises (optional — uses customize screen)
 *     3. START → WorkoutRunActivity (quick_start context)
 *
 * All sessions created here are saved with context = "quick_start"
 * and grouped under a shared groupId.
 */
class QuickStartActivity : AppCompatActivity() {

    companion object {
        fun createIntent(context: android.content.Context) =
            Intent(context, QuickStartActivity::class.java)
    }

    private lateinit var binding: ActivityQuickStartBinding

    /** All exercises loaded from repository */
    private var allExercises: List<ExerciseConfig> = emptyList()

    /** Filtered list currently shown */
    private var filteredExercises: List<ExerciseConfig> = emptyList()

    /** Selected exercise slugs (maintains order of selection) */
    private val selectedSlugs = mutableListOf<String>()

    private lateinit var adapter: ExercisePickerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickStartBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecycler()
        setupSearch()
        setupStartButton()
        loadExercises()
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecycler() {
        adapter = ExercisePickerAdapter()
        binding.rvExercises.layoutManager = LinearLayoutManager(this)
        binding.rvExercises.adapter = adapter
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                filterExercises(s?.toString() ?: "")
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupStartButton() {
        binding.btnStart.setOnClickListener {
            if (selectedSlugs.isEmpty()) {
                MaterialAlertDialogBuilder(this)
                    .setMessage(getString(R.string.quick_start_select_error))
                    .setPositiveButton(getString(R.string.done)) { d, _ -> d.dismiss() }
                    .show()
                return@setOnClickListener
            }

            val workoutConfig = buildWorkoutConfig()
            MovitTrainingEntryNavigator.openWorkoutSessionWithLocalConfig(
                context = this,
                workoutId = "quick-start-${System.currentTimeMillis()}",
                workoutConfigJson = Gson().toJson(workoutConfig),
            )
        }
        updateStartButton()
    }

    // ── Data Loading ───────────────────────────────────────────────────────────

    private fun loadExercises() {
        binding.progressLoading.visibility = View.VISIBLE

        lifecycleScope.launch {
            val repo = ExerciseRepository.getInstance(this@QuickStartActivity)
            withContext(Dispatchers.IO) {
                repo.initialize(autoSync = false)
            }
            allExercises = repo.getAllExercises().sortedBy { ex ->
                ex.name.get(currentLanguage).ifBlank { ex.name.en }
            }
            filteredExercises = allExercises
            binding.progressLoading.visibility = View.GONE
            adapter.notifyDataSetChanged()
        }
    }

    private fun filterExercises(query: String) {
        filteredExercises = if (query.isBlank()) {
            allExercises
        } else {
            allExercises.filter { ex ->
                ExerciseSearchMatcher.matches(ex, query, currentLanguage)
            }
        }
        adapter.notifyDataSetChanged()
    }

    // ── Selection ──────────────────────────────────────────────────────────────

    private fun toggleSelection(slug: String) {
        if (selectedSlugs.contains(slug)) {
            selectedSlugs.remove(slug)
        } else {
            selectedSlugs.add(slug)
        }
        updateStartButton()
        adapter.notifyDataSetChanged()
    }

    private fun updateStartButton() {
        val count = selectedSlugs.size
        binding.btnStart.isEnabled = count > 0
        binding.btnStart.text = if (count > 0) {
            getString(R.string.quick_start_button_format, count)
        } else {
            getString(R.string.quick_start_select_prompt)
        }
    }

    // ── Build Config ───────────────────────────────────────────────────────────

    private fun buildWorkoutConfig(): WorkoutConfig {
        val language = currentLanguage
        val exercises = selectedSlugs.mapNotNull { slug ->
            allExercises.find { it.fileName == slug }
        }.map { ex ->
            WorkoutExercise(
                exercise = ex.fileName,
                variantIndex = 0,
                targetReps = 10,
                sets = 3,
                restBetweenSetsMs = 30_000L,
                restAfterExerciseMs = 60_000L
            )
        }

        return WorkoutConfig(
            name = LocalizedText(ar = "تمرين سريع", en = "Quick Workout"),
            description = null,
            exercises = exercises
        )
    }

    // ── Adapter ────────────────────────────────────────────────────────────────

    inner class ExercisePickerAdapter : RecyclerView.Adapter<ExercisePickerAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvExerciseName)
            val tvMuscle: TextView = view.findViewById(R.id.tvExerciseMuscle)
            val checkBox: CheckBox = view.findViewById(R.id.checkExercise)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.item_exercise_picker, parent, false)
        )

        override fun getItemCount() = filteredExercises.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ex = filteredExercises[position]
            val language = currentLanguage
            val isSelected = selectedSlugs.contains(ex.fileName)

            holder.tvName.text = ex.name.get(language).ifBlank { ex.name.en }
            holder.tvMuscle.text = ex.fileName.replace('_', ' ').replaceFirstChar { it.uppercase() }
            holder.checkBox.isChecked = isSelected

            // CheckBox is not independently clickable — itemView click drives all toggling
            // to avoid double-toggle from click event propagation.
            holder.checkBox.isClickable = false
            holder.checkBox.isFocusable = false
            holder.itemView.setOnClickListener { toggleSelection(ex.fileName) }
        }
    }
}
