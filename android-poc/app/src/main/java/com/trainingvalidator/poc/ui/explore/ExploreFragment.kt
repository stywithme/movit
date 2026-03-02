package com.trainingvalidator.poc.ui.explore

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.trainingvalidator.poc.ui.utils.currentLanguage
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentExploreBinding
import com.trainingvalidator.poc.network.ExploreData
import com.trainingvalidator.poc.network.ExploreExerciseItem
import com.trainingvalidator.poc.network.ExploreLevelItem
import com.trainingvalidator.poc.network.ExploreProgramItem
import com.trainingvalidator.poc.network.ExploreWorkoutItem
import com.trainingvalidator.poc.network.LocalizedName
import com.trainingvalidator.poc.storage.ExploreRepository
import com.trainingvalidator.poc.ui.exercises.ExerciseListActivity
import com.trainingvalidator.poc.ui.level.LevelProfileActivity
import com.trainingvalidator.poc.ui.train.PreWorkoutActivity
import com.trainingvalidator.poc.ui.programs.ProgramDetailActivity
import com.trainingvalidator.poc.ui.programs.ProgramListActivity
import com.trainingvalidator.poc.ui.workouts.WorkoutDetailActivity
import com.trainingvalidator.poc.ui.workouts.WorkoutListActivity
import com.trainingvalidator.poc.assessment.ui.PreScreeningActivity
import com.trainingvalidator.poc.ui.profile.ProfileActivity
import kotlinx.coroutines.launch

/**
 * ExploreFragment - A modern Discovery Hub for the app.
 * Uses a Hero card for featured content and categorized horizontal lists.
 */
class ExploreFragment : Fragment() {

    private var _binding: FragmentExploreBinding? = null
    private val binding get() = _binding!!

    private lateinit var exploreRepository: ExploreRepository

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

        binding.headerLevels.tvSectionTitle.text = getString(R.string.section_levels)
        binding.headerPrograms.tvSectionTitle.text = getString(R.string.section_top_programs)
        binding.headerWorkouts.tvSectionTitle.text = getString(R.string.section_quick_workouts)
        binding.headerExercises.tvSectionTitle.text = getString(R.string.section_exercise_library)

        setupLists()
        setupListeners()

        exploreRepository = ExploreRepository.getInstance(requireContext())
        loadData()
    }

    private fun setupLists() {
        binding.rvLevels.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvPrograms.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvWorkouts.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvExercises.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
    }

    private fun setupListeners() {
        // Direct click on the image view instead of container
        view?.findViewById<ImageView>(R.id.ivAvatar)?.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }
        
        binding.headerLevels.tvViewAll.setOnClickListener {
            startActivity(Intent(requireContext(), LevelProfileActivity::class.java))
        }
        binding.headerPrograms.tvViewAll.setOnClickListener {
            startActivity(Intent(requireContext(), ProgramListActivity::class.java))
        }
        binding.headerWorkouts.tvViewAll.setOnClickListener {
            startActivity(Intent(requireContext(), WorkoutListActivity::class.java))
        }
        binding.headerExercises.tvViewAll.setOnClickListener {
            startActivity(Intent(requireContext(), ExerciseListActivity::class.java))
        }
    }

    private fun loadData() {
        exploreRepository.getCachedData()?.let { renderData(it) }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                exploreRepository.syncFromServer(limit = 8)?.let {
                    if (_binding != null) renderData(it)
                }
            } catch (e: Exception) {
                Log.w("ExploreFragment", "Background explore sync failed", e)
            }
        }
    }

    private fun renderData(data: ExploreData) {
        val language = requireContext().currentLanguage

        // 1. Handle Hero Section (Featured)
        setupHeroSection(data, language)

        // 2. Levels
        val levels = if (data.levels.isNotEmpty()) {
            data.levels
        } else {
            listOf(
                ExploreLevelItem(
                    number = 0,
                    code = "assessment",
                    name = LocalizedName(en = "Assessment", ar = "???????"),
                    description = LocalizedName(en = "Start mobility and readiness assessment", ar = "???? ????? ?????? ?????????")
                )
            )
        }
        binding.rvLevels.adapter = ExploreLevelsAdapter(levels) { item ->
            if (item.code == "assessment") {
                startActivity(Intent(requireContext(), PreScreeningActivity::class.java))
            } else {
                startActivity(Intent(requireContext(), LevelProfileActivity::class.java))
            }
        }

        // 3. Programs
        binding.rvPrograms.adapter = ExploreProgramAdapter(data.programs) {
            startActivity(Intent(requireContext(), ProgramDetailActivity::class.java).apply {
                putExtra(ProgramDetailActivity.EXTRA_PROGRAM_SLUG, it.slug)
            })
        }

        // 4. Workouts
        binding.rvWorkouts.adapter = ExploreWorkoutAdapter(data.workouts) {
            startActivity(Intent(requireContext(), WorkoutDetailActivity::class.java).apply {
                putExtra(WorkoutDetailActivity.EXTRA_WORKOUT_NAME, it.slug)
            })
        }

        // 5. Exercises
        binding.rvExercises.adapter = ExploreExerciseAdapter(data.exercises) {
            startActivity(Intent(requireContext(), PreWorkoutActivity::class.java).apply {
                putExtra(PreWorkoutActivity.EXTRA_EXERCISE_NAME, it.slug)
            })
        }

        // Visibility toggles
        binding.headerLevels.root.visibility = if (levels.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvLevels.visibility = if (levels.isNotEmpty()) View.VISIBLE else View.GONE
        
        binding.headerPrograms.root.visibility = if (data.programs.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvPrograms.visibility = if (data.programs.isNotEmpty()) View.VISIBLE else View.GONE
        
        binding.headerWorkouts.root.visibility = if (data.workouts.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvWorkouts.visibility = if (data.workouts.isNotEmpty()) View.VISIBLE else View.GONE
        
        binding.headerExercises.root.visibility = if (data.exercises.isNotEmpty()) View.VISIBLE else View.GONE
        binding.rvExercises.visibility = if (data.exercises.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun setupHeroSection(data: ExploreData, language: String) {
        val heroContainer = binding.root.findViewById<View>(R.id.layoutHeroContainer)
        val tvHeroTitle = binding.root.findViewById<TextView>(R.id.tvHeroTitle)
        val tvHeroSubtitle = binding.root.findViewById<TextView>(R.id.tvHeroSubtitle)
        val btnHeroAction = binding.root.findViewById<MaterialButton>(R.id.btnHeroAction)
        
        // Find a featured program first, if none, featured workout
        // Assuming the repository/backend returns isFeatured items at the top of the lists
        val featuredProgram = data.programs.firstOrNull { true } // Ideally check isFeatured, but since we sort by it, the first is usually the best candidate if it exists
        
        if (featuredProgram != null) {
            heroContainer.visibility = View.VISIBLE
            tvHeroTitle.text = if (language == "ar") featuredProgram.name.ar else featuredProgram.name.en
            tvHeroSubtitle.text = "${featuredProgram.durationWeeks} Weeks • ${featuredProgram.difficulty.replaceFirstChar { it.uppercase() }}"
            btnHeroAction.text = "View Program"
            btnHeroAction.setOnClickListener {
                startActivity(Intent(requireContext(), ProgramDetailActivity::class.java).apply {
                    putExtra(ProgramDetailActivity.EXTRA_PROGRAM_SLUG, featuredProgram.slug)
                })
            }
        } else {
            heroContainer.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun cardWidth(fraction: Float): Int {
        val displayMetrics = requireContext().resources.displayMetrics
        return (displayMetrics.widthPixels * fraction).toInt()
    }

    inner class ExploreProgramAdapter(
        private val items: List<ExploreProgramItem>,
        private val onClick: (ExploreProgramItem) -> Unit
    ) : RecyclerView.Adapter<ExploreProgramAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvProgramName)
            val tvDuration: TextView = view.findViewById(R.id.tvDuration)
            val tvDifficulty: TextView = view.findViewById(R.id.tvDifficulty)
            val root: View = view
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_program_browse_card, parent, false)
            view.layoutParams = RecyclerView.LayoutParams(cardWidth(0.70f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = 32
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val program = items[position]
            val language = requireContext().currentLanguage
            holder.tvName.text = if (language == "ar") program.name.ar else program.name.en
            holder.tvDuration.text = "${program.durationWeeks} weeks"
            holder.tvDifficulty.text = program.difficulty.replaceFirstChar { it.uppercase() }
            holder.root.setOnClickListener { onClick(program) }
        }

        override fun getItemCount() = items.size
    }

    inner class ExploreWorkoutAdapter(
        private val items: List<ExploreWorkoutItem>,
        private val onClick: (ExploreWorkoutItem) -> Unit
    ) : RecyclerView.Adapter<ExploreWorkoutAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvWorkoutName)
            val tvMeta: TextView = view.findViewById(R.id.tvWorkoutMeta)
            val root: View = view
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_explore_workout_compact, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val workout = items[position]
            val language = requireContext().currentLanguage
            holder.tvName.text = if (language == "ar") workout.name.ar else workout.name.en
            
            val duration = workout.estimatedDurationMin?.let { "$it min" } ?: "Varies"
            holder.tvMeta.text = "$duration"
            
            holder.root.setOnClickListener { onClick(workout) }
        }

        override fun getItemCount() = items.size
    }

    inner class ExploreExerciseAdapter(
        private val items: List<ExploreExerciseItem>,
        private val onClick: (ExploreExerciseItem) -> Unit
    ) : RecyclerView.Adapter<ExploreExerciseAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvExerciseName)
            val root: View = view
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_explore_exercise_compact, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val exercise = items[position]
            val language = requireContext().currentLanguage
            holder.tvName.text = if (language == "ar") exercise.name.ar else exercise.name.en
            holder.root.setOnClickListener { onClick(exercise) }
        }

        override fun getItemCount() = items.size
    }

    inner class ExploreLevelsAdapter(
        private val items: List<ExploreLevelItem>,
        private val onClick: (ExploreLevelItem) -> Unit
    ) : RecyclerView.Adapter<ExploreLevelsAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvLevelNumber: TextView = view.findViewById(R.id.tvLevelNumber)
            val tvLevelName: TextView = view.findViewById(R.id.tvLevelName)
            val tvLevelDesc: TextView = view.findViewById(R.id.tvLevelDesc)
            val root: View = view
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_explore_level_card, parent, false)
            view.layoutParams = RecyclerView.LayoutParams(cardWidth(0.60f), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = 32
            }
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val level = items[position]
            val language = requireContext().currentLanguage
            
            if (level.number == 0) {
                holder.tvLevelNumber.text = "START"
            } else {
                holder.tvLevelNumber.text = "LEVEL ${level.number}"
            }
            
            holder.tvLevelName.text = if (language == "ar") level.name.ar else level.name.en
            holder.tvLevelDesc.text = if (language == "ar") level.description?.ar else level.description?.en
            
            holder.root.setOnClickListener { onClick(level) }
        }

        override fun getItemCount() = items.size
    }
}