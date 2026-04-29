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

    /** Difficulty filter; null = all */
    private var selectedDifficulty: String? = null
    /** Training goal filter; null = any */
    private var selectedGoal: String? = null

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
                selectedDifficulty = null
                selectedGoal = null
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

        fun makeChip(label: String, tag: String): Chip {
            return Chip(this).apply {
                text = label
                isCheckable = true
                this.tag = tag
            }
        }

        val allDiff = makeChip(getString(R.string.program_filter_all), "")
        allDiff.isChecked = true
        binding.chipGroupDifficulty.addView(allDiff)

        listOf("beginner", "intermediate", "advanced")
            .filter { d -> allPrograms.any { it.difficulty.equals(d, ignoreCase = true) } }
            .forEach { d ->
                binding.chipGroupDifficulty.addView(makeChip(formatDifficulty(d), d))
            }

        val goals = allPrograms.mapNotNull { it.trainingGoal }.distinct().sorted()
        if (goals.size <= 1) {
            binding.scrollGoalFilters.visibility = View.GONE
        } else {
            binding.scrollGoalFilters.visibility = View.VISIBLE
            val anyGoal = makeChip(getString(R.string.program_filter_goal_any), "")
            anyGoal.isChecked = true
            binding.chipGroupGoal.addView(anyGoal)
            goals.forEach { g ->
                val label = g.replace('_', ' ').lowercase()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                binding.chipGroupGoal.addView(makeChip(label, g))
            }
        }

        binding.chipGroupDifficulty.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedIds.first())
            val tag = chip.tag as? String ?: ""
            selectedDifficulty = tag.ifBlank { null }
            applyFilters()
        }
        binding.chipGroupGoal.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isEmpty()) return@setOnCheckedStateChangeListener
            val chip = group.findViewById<Chip>(checkedIds.first())
            val tag = chip.tag as? String ?: ""
            selectedGoal = tag.ifBlank { null }
            applyFilters()
        }

        binding.scrollDifficultyFilters.visibility =
            if (allPrograms.size > 1) View.VISIBLE else View.GONE
    }

    private fun applyFilters() {
        displayedPrograms.clear()
        displayedPrograms.addAll(
            allPrograms.filter { p ->
                val dOk = selectedDifficulty == null ||
                    p.difficulty.equals(selectedDifficulty, ignoreCase = true)
                val gOk = selectedGoal == null ||
                    p.trainingGoal?.equals(selectedGoal, ignoreCase = true) == true
                dOk && gOk
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
            val tvSessions: TextView = view.findViewById(R.id.tvSessions)
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

            val totalSessions = program.weeks.sumOf { week ->
                week.days.sumOf { day -> day.sessions.size }
            }
            val weeklyTarget = program.weeklySessionTarget?.takeIf { it > 0 }
            val sessionLabel = if (weeklyTarget != null) {
                getString(
                    R.string.program_card_sessions_weekly_format,
                    totalSessions,
                    weeklyTarget
                )
            } else {
                getString(R.string.sessions_count_format, totalSessions)
            }

            holder.tvWeeks.text = getString(R.string.weeks_count_format, program.durationWeeks)
            holder.tvSessions.text = sessionLabel
            holder.tvDifficulty.text = formatDifficulty(program.difficulty)
            holder.tvFeaturedBadge.visibility = if (program.isFeatured) View.VISIBLE else View.GONE

            val metaParts = buildList {
                program.trainingGoal?.let { add(it.replace('_', ' ')) }
                program.estimatedSessionMinutes?.takeIf { it > 0 }?.let { add("${it} min") }
                program.targetEquipment.firstOrNull()?.let { add(it) }
            }
            if (metaParts.isNotEmpty()) {
                val extra = metaParts.joinToString(" · ")
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

    private fun formatDifficulty(difficulty: String): String {
        if (difficulty.isBlank()) return getString(R.string.workout_detail_default_difficulty)
        val normalized = difficulty.replace('_', ' ').lowercase()
        return normalized.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
