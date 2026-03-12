package com.trainingvalidator.poc.ui.explore

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentExploreBinding
import com.trainingvalidator.poc.storage.ExerciseRepository
import com.trainingvalidator.poc.storage.WorkoutRepository
import com.trainingvalidator.poc.training.models.ExerciseConfig
import com.trainingvalidator.poc.training.models.WorkoutConfig
import com.trainingvalidator.poc.ui.exercises.ExerciseListActivity
import com.trainingvalidator.poc.ui.profile.ProfileActivity
import com.trainingvalidator.poc.ui.train.PreWorkoutActivity
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.ui.workouts.WorkoutDetailActivity
import com.trainingvalidator.poc.ui.workouts.WorkoutListActivity
import kotlinx.coroutines.launch

/**
 * ExploreFragment - Mobile-first library focused on workouts and exercises.
 */
class ExploreFragment : Fragment() {

    companion object {
        private const val TAG = "ExploreFragment"
    }

    private enum class ContentFilter {
        ALL,
        WORKOUTS,
        EXERCISES
    }

    private enum class WorkoutFilter {
        ALL,
        EASY,
        MEDIUM,
        HARD,
        SHORT
    }

    private var _binding: FragmentExploreBinding? = null
    private val binding get() = _binding!!

    private val workoutItems = mutableListOf<WorkoutConfig>()
    private val exerciseItems = mutableListOf<ExerciseConfig>()
    private val filteredWorkoutItems = mutableListOf<WorkoutConfig>()
    private val filteredExerciseItems = mutableListOf<ExerciseConfig>()

    private val exerciseRepository by lazy { ExerciseRepository.getInstance(requireContext()) }
    private val workoutRepository by lazy { WorkoutRepository.getInstance(requireContext()) }

    private var currentContentFilter = ContentFilter.ALL
    private var currentWorkoutFilter = WorkoutFilter.ALL
    private var currentExerciseCategory: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExploreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStaticContent()
        setupLists()
        setupListeners()
        loadLibrary()
    }

    private fun setupStaticContent() {
        binding.headerWorkouts.tvSectionTitle.text = getString(R.string.workouts)
        binding.headerExercises.tvSectionTitle.text = getString(R.string.exercises)
        binding.headerWorkouts.tvSectionCount.text = "0"
        binding.headerExercises.tvSectionCount.text = "0"
    }

    private fun setupLists() {
        binding.rvWorkouts.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvExercises.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        binding.rvWorkouts.adapter = ExploreWorkoutAdapter(filteredWorkoutItems) { workout ->
            openWorkout(workout)
        }
        binding.rvExercises.adapter = ExploreExerciseAdapter(filteredExerciseItems) { exercise ->
            openExercise(exercise)
        }
    }

    private fun setupListeners() {
        binding.ivAvatar.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }

        binding.headerWorkouts.tvViewAll.setOnClickListener {
            startActivity(Intent(requireContext(), WorkoutListActivity::class.java))
        }

        binding.headerExercises.tvViewAll.setOnClickListener {
            startActivity(Intent(requireContext(), ExerciseListActivity::class.java))
        }

        binding.etSearch.addTextChangedListener {
            applyFilters()
        }

        binding.chipGroupPrimaryFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            currentContentFilter = when (checkedIds.firstOrNull()) {
                R.id.chipFilterWorkouts -> ContentFilter.WORKOUTS
                R.id.chipFilterExercises -> ContentFilter.EXERCISES
                else -> ContentFilter.ALL
            }
            applyFilters()
        }

        binding.chipGroupWorkoutFilters.setOnCheckedStateChangeListener { _, checkedIds ->
            currentWorkoutFilter = when (checkedIds.firstOrNull()) {
                R.id.chipWorkoutBeginner -> WorkoutFilter.EASY
                R.id.chipWorkoutIntermediate -> WorkoutFilter.MEDIUM
                R.id.chipWorkoutAdvanced -> WorkoutFilter.HARD
                R.id.chipWorkoutShort -> WorkoutFilter.SHORT
                else -> WorkoutFilter.ALL
            }
            applyFilters()
        }

        binding.chipGroupExerciseFilters.setOnCheckedStateChangeListener { group, checkedIds ->
            val chipId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val selectedChip = group.findViewById<Chip>(chipId)
            currentExerciseCategory = (selectedChip?.tag as? String)?.takeIf { it.isNotBlank() }
            applyFilters()
        }
    }

    private fun loadLibrary() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                exerciseRepository.initialize(autoSync = false)
                workoutRepository.initialize()
                if (_binding != null) {
                    renderLibraryData(
                        workouts = workoutRepository.getAllWorkouts(),
                        exercises = exerciseRepository.getAllExercises()
                    )
                }

                launch {
                    syncLatestContent()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load explore library", e)
                if (_binding != null) {
                    renderLibraryData(emptyList(), emptyList())
                }
            }
        }
    }

    private suspend fun syncLatestContent() {
        try {
            exerciseRepository.checkForUpdates()
            if (_binding != null) {
                renderLibraryData(
                    workouts = workoutRepository.getAllWorkouts(),
                    exercises = exerciseRepository.getAllExercises()
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Background library sync failed", e)
        }
    }

    private fun renderLibraryData(
        workouts: List<WorkoutConfig>,
        exercises: List<ExerciseConfig>
    ) {
        val language = requireContext().currentLanguage

        workoutItems.clear()
        workoutItems.addAll(workouts.sortedBy { localizedWorkoutName(it, language) })

        exerciseItems.clear()
        exerciseItems.addAll(exercises.sortedBy { localizedExerciseName(it, language) })

        if (currentExerciseCategory != null &&
            exerciseItems.none { it.category.code.equals(currentExerciseCategory, ignoreCase = true) }
        ) {
            currentExerciseCategory = null
        }

        binding.tvWorkoutsCount.text = workoutItems.size.toString()
        binding.tvExercisesCount.text = exerciseItems.size.toString()
        binding.headerWorkouts.tvViewAll.visibility =
            if (workoutItems.isNotEmpty()) View.VISIBLE else View.GONE
        binding.headerExercises.tvViewAll.visibility =
            if (exerciseItems.isNotEmpty()) View.VISIBLE else View.GONE

        populateExerciseFilterChips()
        applyFilters()
    }

    private fun populateExerciseFilterChips() {
        val selectedCategory = currentExerciseCategory
        val language = requireContext().currentLanguage

        binding.chipGroupExerciseFilters.removeAllViews()
        addExerciseFilterChip(
            label = getString(R.string.all),
            categoryCode = null,
            isChecked = selectedCategory == null
        )

        val categories = exerciseItems
            .mapNotNull { exercise ->
                exercise.category.code.takeIf { it.isNotBlank() }?.let { code -> code to exercise }
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, List<ExerciseConfig>>> { it.value.size }
                    .thenBy { categoryLabel(it.value.first(), language) }
            )

        categories.take(6).forEach { entry ->
            addExerciseFilterChip(
                label = categoryLabel(entry.value.first(), language),
                categoryCode = entry.key,
                isChecked = entry.key == selectedCategory
            )
        }

        if (binding.chipGroupExerciseFilters.checkedChipId == View.NO_ID &&
            binding.chipGroupExerciseFilters.childCount > 0
        ) {
            (binding.chipGroupExerciseFilters.getChildAt(0) as? Chip)?.isChecked = true
            currentExerciseCategory = null
        }
    }

    private fun addExerciseFilterChip(
        label: String,
        categoryCode: String?,
        isChecked: Boolean
    ) {
        val chipContext = ContextThemeWrapper(requireContext(), R.style.Widget_WayToFix_Chip_Filter)
        val chip = Chip(chipContext, null).apply {
            text = label
            isCheckable = true
            isClickable = true
            tag = categoryCode
            this.isChecked = isChecked
        }
        binding.chipGroupExerciseFilters.addView(chip)
    }

    private fun applyFilters() {
        if (_binding == null) return

        val query = binding.etSearch.text?.toString()?.trim().orEmpty()

        filteredWorkoutItems.clear()
        filteredWorkoutItems.addAll(
            workoutItems.filter { workout ->
                matchesWorkoutFilter(workout) && matchesWorkoutSearch(workout, query)
            }
        )

        filteredExerciseItems.clear()
        filteredExerciseItems.addAll(
            exerciseItems.filter { exercise ->
                matchesExerciseFilter(exercise) && matchesExerciseSearch(exercise, query)
            }
        )

        binding.rvWorkouts.adapter?.notifyDataSetChanged()
        binding.rvExercises.adapter?.notifyDataSetChanged()
        updateFilteredState()
    }

    private fun matchesWorkoutFilter(workout: WorkoutConfig): Boolean {
        return when (currentWorkoutFilter) {
            WorkoutFilter.ALL -> true
            WorkoutFilter.EASY -> matchesAnyValue(workout.difficulty, "beginner", "easy")
            WorkoutFilter.MEDIUM -> matchesAnyValue(workout.difficulty, "intermediate", "medium")
            WorkoutFilter.HARD -> matchesAnyValue(workout.difficulty, "advanced", "hard")
            WorkoutFilter.SHORT -> resolvedWorkoutDurationMinutes(workout) in 1..20
        }
    }

    private fun matchesWorkoutSearch(workout: WorkoutConfig, query: String): Boolean {
        val language = requireContext().currentLanguage
        return matchesQuery(
            query,
            localizedWorkoutName(workout, language),
            workout.name.en,
            workout.name.ar,
            workout.description?.get(language),
            workout.description?.en,
            workout.description?.ar,
            workout.fileName,
            formatDifficulty(workout.difficulty)
        )
    }

    private fun matchesExerciseFilter(exercise: ExerciseConfig): Boolean {
        val selectedCategory = currentExerciseCategory ?: return true
        return exercise.category.code.equals(selectedCategory, ignoreCase = true)
    }

    private fun matchesExerciseSearch(exercise: ExerciseConfig, query: String): Boolean {
        val language = requireContext().currentLanguage
        return matchesQuery(
            query,
            localizedExerciseName(exercise, language),
            exercise.name.en,
            exercise.name.ar,
            categoryLabel(exercise, language),
            exercise.fileName,
            exercise.tags.joinToString(" "),
            exercise.muscles.joinToString(" ")
        )
    }

    private fun updateFilteredState() {
        val showWorkoutsSection = currentContentFilter != ContentFilter.EXERCISES
        val showExercisesSection = currentContentFilter != ContentFilter.WORKOUTS

        val hasVisibleResults =
            (showWorkoutsSection && filteredWorkoutItems.isNotEmpty()) ||
                (showExercisesSection && filteredExerciseItems.isNotEmpty())

        binding.sectionWorkoutsContainer.visibility =
            if (showWorkoutsSection) View.VISIBLE else View.GONE
        binding.sectionExercisesContainer.visibility =
            if (showExercisesSection) View.VISIBLE else View.GONE

        binding.rvWorkouts.visibility =
            if (showWorkoutsSection && filteredWorkoutItems.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvExercises.visibility =
            if (showExercisesSection && filteredExerciseItems.isNotEmpty()) View.VISIBLE else View.GONE

        binding.tvWorkoutsEmpty.visibility =
            if (showWorkoutsSection && filteredWorkoutItems.isEmpty() && hasVisibleResults) {
                View.VISIBLE
            } else {
                View.GONE
            }
        binding.tvExercisesEmpty.visibility =
            if (showExercisesSection && filteredExerciseItems.isEmpty() && hasVisibleResults) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.layoutEmpty.visibility = if (hasVisibleResults) View.GONE else View.VISIBLE
        binding.tvResultsSummary.text = getString(
            R.string.explore_results_summary_format,
            filteredWorkoutItems.size,
            filteredExerciseItems.size
        )
        binding.headerWorkouts.tvSectionCount.text = filteredWorkoutItems.size.toString()
        binding.headerExercises.tvSectionCount.text = filteredExerciseItems.size.toString()
    }

    private fun openWorkout(workout: WorkoutConfig) {
        startActivity(Intent(requireContext(), WorkoutDetailActivity::class.java).apply {
            putExtra(WorkoutDetailActivity.EXTRA_WORKOUT_NAME, workout.fileName)
        })
    }

    private fun openExercise(exercise: ExerciseConfig) {
        startActivity(Intent(requireContext(), PreWorkoutActivity::class.java).apply {
            putExtra(PreWorkoutActivity.EXTRA_EXERCISE_NAME, exercise.fileName)
        })
    }

    private fun localizedWorkoutName(workout: WorkoutConfig, language: String): String {
        return workout.name.get(language).ifBlank { workout.name.en }
    }

    private fun localizedExerciseName(exercise: ExerciseConfig, language: String): String {
        return exercise.name.get(language).ifBlank { exercise.name.en }
    }

    private fun categoryLabel(exercise: ExerciseConfig, language: String): String {
        return exercise.category.name.get(language)
            .ifBlank { exercise.category.name.en }
            .ifBlank { humanizeCode(exercise.category.code) }
    }

    private fun resolvedWorkoutDurationMinutes(workout: WorkoutConfig): Int {
        val minutes = workout.estimatedDurationMin ?: (workout.getEstimatedDurationMs() / 60000L).toInt()
        return minutes.coerceAtLeast(1)
    }

    private fun formatDifficulty(difficulty: String?): String {
        if (difficulty.isNullOrBlank()) return getString(R.string.workout_detail_default_difficulty)
        val normalized = difficulty.replace('_', ' ').trim().lowercase()
        return normalized.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun humanizeCode(code: String): String {
        return code
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun matchesQuery(query: String, vararg values: String?): Boolean {
        if (query.isBlank()) return true
        return values.any { value -> value?.contains(query, ignoreCase = true) == true }
    }

    private fun matchesAnyValue(value: String?, vararg keywords: String): Boolean {
        if (value.isNullOrBlank()) return false
        return keywords.any { keyword -> value.contains(keyword, ignoreCase = true) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class ExploreWorkoutAdapter(
        private val items: List<WorkoutConfig>,
        private val onClick: (WorkoutConfig) -> Unit
    ) : RecyclerView.Adapter<ExploreWorkoutAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvType: TextView = view.findViewById(R.id.tvWorkoutType)
            val tvName: TextView = view.findViewById(R.id.tvWorkoutName)
            val tvMeta: TextView = view.findViewById(R.id.tvWorkoutMeta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_explore_workout_compact, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val workout = items[position]
            val language = requireContext().currentLanguage
            holder.tvType.text = formatDifficulty(workout.difficulty)
            holder.tvName.text = localizedWorkoutName(workout, language)
            holder.tvMeta.text = getString(
                R.string.ds_exercises_meta_format,
                workout.getTotalExerciseCount(),
                resolvedWorkoutDurationMinutes(workout)
            )
            holder.itemView.setOnClickListener { onClick(workout) }
        }

        override fun getItemCount() = items.size
    }

    inner class ExploreExerciseAdapter(
        private val items: List<ExerciseConfig>,
        private val onClick: (ExerciseConfig) -> Unit
    ) : RecyclerView.Adapter<ExploreExerciseAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvCategory: TextView = view.findViewById(R.id.tvExerciseCategory)
            val tvName: TextView = view.findViewById(R.id.tvExerciseName)
            val tvMeta: TextView = view.findViewById(R.id.tvExerciseMeta)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_explore_exercise_compact, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val exercise = items[position]
            val language = requireContext().currentLanguage
            val muscleCount = exercise.muscles.distinct().size

            holder.tvCategory.text = categoryLabel(exercise, language)
            holder.tvName.text = localizedExerciseName(exercise, language)
            holder.tvMeta.text = if (muscleCount > 0) {
                getString(R.string.explore_exercise_meta_format, muscleCount)
            } else {
                getString(R.string.explore_exercise_meta_fallback)
            }
            holder.itemView.setOnClickListener { onClick(exercise) }
        }

        override fun getItemCount() = items.size
    }
}