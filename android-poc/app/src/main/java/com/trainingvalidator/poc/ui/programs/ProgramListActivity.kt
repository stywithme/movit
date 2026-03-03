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
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityProgramListBinding
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.training.models.ProgramConfig
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ProgramListActivity - Shows available training programs
 */
class ProgramListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ProgramListActivity"
    }

    private lateinit var binding: ActivityProgramListBinding
    private val programs = mutableListOf<ProgramConfig>()

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
        binding.rvPrograms.adapter = ProgramAdapter(programs) { program ->
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

            // If cache is empty, trigger sync and reload
            if (loaded.isEmpty()) {
                withContext(Dispatchers.IO) {
                    val exerciseRepo = com.trainingvalidator.poc.storage.ExerciseRepository.getInstance(this@ProgramListActivity)
                    exerciseRepo.initialize(autoSync = true)
                    repository.reloadFromCache()
                }
                loaded = repository.getAllPrograms()
            }

            if (loaded.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvPrograms.visibility = View.GONE
            } else {
                programs.clear()
                programs.addAll(loaded)
                binding.rvPrograms.adapter?.notifyDataSetChanged()
                binding.layoutEmpty.visibility = View.GONE
                binding.rvPrograms.visibility = View.VISIBLE
            }
        }
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

            holder.tvWeeks.text = getString(R.string.weeks_count_format, program.durationWeeks)
            holder.tvSessions.text = getString(R.string.sessions_count_format, totalSessions)
            holder.tvDifficulty.text = formatDifficulty(program.difficulty)

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
