package com.trainingvalidator.poc.ui.programs

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
import com.google.android.material.chip.Chip
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.ui.utils.formatProgramLevelRange
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityProgramListBinding
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.training.models.ProgramConfig
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ProgramListActivity - Shows available training programs with optional filters.
 */
class ProgramListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProgramListBinding
    private val allPrograms = mutableListOf<ProgramConfig>()
    private val displayedPrograms = mutableListOf<ProgramConfig>()


    /** Level-range filter; null = all. Tag is "min,max". */
    private var selectedLevelKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityProgramListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadPrograms()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.rvPrograms.layoutManager = LinearLayoutManager(this)
        binding.rvPrograms.adapter = ProgramAdapter(displayedPrograms) { program ->
            openProgram(program)
        }
    }

    private fun loadPrograms() {
        lifecycleScope.launch {
            val repository = ProgramRepository.getInstance(this@ProgramListActivity)

            withContext(Dispatchers.IO) {
                repository.initialize()
            }

            var loaded = repository.getAllPrograms()

            if (loaded.isEmpty()) {
                withContext(Dispatchers.IO) {
                    val exerciseRepo =
                        com.trainingvalidator.poc.storage.ExerciseRepository.getInstance(this@ProgramListActivity)
                    exerciseRepo.initialize(autoSync = true)
                    repository.reloadFromCache()
                }
                loaded = repository.getAllPrograms()
            }

            if (loaded.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvPrograms.visibility = View.GONE
                binding.scrollDifficultyFilters.visibility = View.GONE
                binding.scrollGoalFilters.visibility = View.GONE
            } else {
                allPrograms.clear()
                allPrograms.addAll(loaded)
                selectedLevelKey = null
                rebuildFilterChips()
                applyFilters()

                binding.layoutEmpty.visibility = View.GONE
                binding.rvPrograms.visibility = View.VISIBLE
            }
        }
    }

    private fun rebuildFilterChips() {
        binding.chipGroupDifficulty.removeAllViews()
        binding.chipGroupGoal.removeAllViews()
        binding.scrollGoalFilters.visibility = View.GONE

        fun makeChip(label: String, tag: String): Chip {
            return Chip(this).apply {
                text = label
                isCheckable = true
                this.tag = tag
            }
        }

        val allRanges = makeChip(getString(R.string.program_filter_all), "")
        allRanges.isChecked = true
        binding.chipGroupDifficulty.addView(allRanges)

        val distinctRanges = allPrograms
            .map { it.levelRangeMin to it.levelRangeMax }
            .distinct()
            .sortedWith(compareBy({ it.first }, { it.second }))

        distinctRanges.forEach { (min, max) ->
            val key = "$min,$max"
            binding.chipGroupDifficulty.addView(
                makeChip(formatProgramLevelRange(min, max), key)
            )
        }

        binding.chipGroupDifficulty.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedIds.first())
            val tag = chip.tag as? String ?: ""
            selectedLevelKey = tag.ifBlank { null }
            applyFilters()
        }

        val showRangeFilter = allPrograms.size > 1 && distinctRanges.size > 1
        binding.scrollDifficultyFilters.visibility =
            if (showRangeFilter) View.VISIBLE else View.GONE
    }

    private fun applyFilters() {
        displayedPrograms.clear()
        displayedPrograms.addAll(
            allPrograms.filter { p ->
                selectedLevelKey == null || run {
                    val parts = selectedLevelKey!!.split(',')
                    if (parts.size != 2) return@run true
                    p.levelRangeMin == parts[0].toInt() && p.levelRangeMax == parts[1].toInt()
                }
            }
        )
        if (displayedPrograms.isEmpty() && allPrograms.isNotEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
            binding.rvPrograms.visibility = View.GONE
        } else {
            binding.layoutEmpty.visibility = View.GONE
            binding.rvPrograms.visibility = View.VISIBLE
        }
        binding.rvPrograms.adapter?.notifyDataSetChanged()
    }

    private fun openProgram(program: ProgramConfig) {
        val intent = Intent(this, ProgramDetailActivity::class.java).apply {
            putExtra(ProgramDetailActivity.EXTRA_PROGRAM_SLUG, program.slug)
        }
        startActivity(intent)
    }

    inner class ProgramAdapter(
        private val items: List<ProgramConfig>,
        private val onClick: (ProgramConfig) -> Unit
    ) : RecyclerView.Adapter<ProgramAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivProgramImage: ImageView = view.findViewById(R.id.ivProgramImage)
            val tvName: TextView = view.findViewById(R.id.tvProgramName)
            val tvDescription: TextView = view.findViewById(R.id.tvDescription)
            val tvWeeks: TextView = view.findViewById(R.id.tvWeeks)
            val tvDifficulty: TextView = view.findViewById(R.id.tvDifficulty)
            val tvWorkouts: TextView = view.findViewById(R.id.tvWorkouts)
            val tvFeaturedBadge: TextView = view.findViewById(R.id.tvFeaturedBadge)
            val btnStart: View = view.findViewById(R.id.btnStart)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_program_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val program = items[position]
            val language = currentLanguage

            holder.tvName.text = program.name.get(language).ifBlank { program.name.en }
            holder.tvDescription.text = program.description?.let { desc ->
                desc.get(language).ifBlank { desc.en }
            } ?: ""

            val totalPlannedWorkouts = program.weeks.orEmpty().sumOf { week ->
                week.days.sumOf { day -> day.workouts.size }
            }
            val weeklyTarget = program.weeklyWorkoutTarget?.takeIf { it > 0 }
            val workoutsLabel = if (weeklyTarget != null) {
                getString(
                    R.string.program_card_workouts_weekly_format,
                    totalPlannedWorkouts,
                    weeklyTarget
                )
            } else {
                getString(R.string.planned_workouts_count_format, totalPlannedWorkouts)
            }

            holder.tvWeeks.text = getString(R.string.weeks_count_format, program.durationWeeks)
            holder.tvWorkouts.text = workoutsLabel
            holder.tvDifficulty.text = formatProgramLevelRange(program.levelRangeMin, program.levelRangeMax)
            holder.tvFeaturedBadge.visibility = if (program.isFeatured) View.VISIBLE else View.GONE

            val metaParts = buildList {
                program.estimatedWorkoutMinutes?.takeIf { it > 0 }?.let { add("${it} min") }
            }
            if (metaParts.isNotEmpty()) {
                val extra = metaParts.joinToString(" Â· ")
                holder.tvDescription.text = buildString {
                    append(holder.tvDescription.text)
                    if (isNotEmpty()) append("\n")
                    append(extra)
                }
            }

            holder.itemView.setOnClickListener { onClick(program) }
            holder.btnStart.setOnClickListener { onClick(program) }
        }

        override fun getItemCount() = items.size
    }
}
