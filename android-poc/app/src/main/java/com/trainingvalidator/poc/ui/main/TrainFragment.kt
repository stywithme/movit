package com.trainingvalidator.poc.ui.main

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentTrainBinding
import com.trainingvalidator.poc.network.TodaySessionData
import com.trainingvalidator.poc.storage.HomeRepository
import com.trainingvalidator.poc.ui.ProgramSessionActivity
import kotlinx.coroutines.launch

/**
 * TrainFragment (previously ProgramsFragment)
 *
 * Implements the Train Page Redesign Plan:
 * 1. The Journey Map
 * 2. Dynamic Hero Header
 * 3. Smart CTA
 * 4. Interactive Session Cards
 */
class TrainFragment : Fragment() {

    private var _binding: FragmentTrainBinding? = null
    private val binding get() = _binding!!

    private lateinit var homeRepository: HomeRepository

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTrainBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        homeRepository = HomeRepository.getInstance(requireContext())
        setupRecyclerView()
        loadData()
    }

    private fun setupRecyclerView() {
        binding.rvSessions.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun loadData() {
        // 1. Offline-first: Render from cache instantly
        homeRepository.getCachedData()?.let { renderData(it) }

        // 2. Sync background
        lifecycleScope.launch {
            try {
                homeRepository.syncFromServer()?.let { renderData(it) }
            } catch (e: Exception) {
                Log.w("TrainFragment", "Background sync failed", e)
            }
        }
    }

    private fun renderData(data: com.trainingvalidator.poc.network.HomeData) {
        val activeProgram = data.activePlan?.programs?.firstOrNull { it.status == "active" }
        val todayPlan = data.todayPlan?.currentProgram

        if (activeProgram == null || todayPlan == null) {
            showEmptyState()
            return
        }

        binding.layoutNoProgramState.visibility = View.GONE
        binding.layoutActiveProgramState.visibility = View.VISIBLE

        val language = java.util.Locale.getDefault().language
        val programName = activeProgram.program?.name?.get(language) ?: activeProgram.program?.name?.get("en") ?: ""
        
        // 1. The Journey Map
        val levelNumber = data.levelProfile?.overallLevel ?: 1
        binding.tvJourneyLevel.text = "Level $levelNumber"
        binding.tvJourneyProgram.text = programName
        binding.tvJourneyPosition.text = "W${todayPlan.weekNumber} • D${todayPlan.dayNumber}"

        // 2. State Resolution (Rest Day, Completed, Ready)
        if (todayPlan.isRestDay) {
            renderRestDayState()
        } else {
            val allCompleted = todayPlan.sessions.isNotEmpty() && todayPlan.sessions.all { it.isCompleted }
            if (allCompleted) {
                renderCompletedState()
            } else {
                renderReadyState(todayPlan.sessions, activeProgram.program?.id, activeProgram.program?.slug, todayPlan.weekNumber, todayPlan.dayNumber)
            }
        }

        // 3. Render Session Cards
        binding.rvSessions.adapter = InteractiveSessionAdapter(todayPlan.sessions) { session ->
            if (!session.isCompleted) {
                startSession(activeProgram.program?.slug, activeProgram.program?.id, todayPlan.weekNumber, todayPlan.dayNumber, session.id)
            }
        }
    }

    private fun showEmptyState() {
        binding.layoutNoProgramState.visibility = View.VISIBLE
        binding.layoutActiveProgramState.visibility = View.GONE

        // Inflate empty state if not done
        if (binding.layoutNoProgramState.childCount == 0) {
            val inflater = LayoutInflater.from(requireContext())
            val emptyStateView = inflater.inflate(R.layout.layout_train_empty_state, binding.layoutNoProgramState, false)
            binding.layoutNoProgramState.addView(emptyStateView)
            
            val btnExplore = emptyStateView.findViewById<MaterialButton>(R.id.btnGoToExplore)
            btnExplore.setOnClickListener {
                (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_explore)
            }
        }
    }

    private fun renderReadyState(
        sessions: List<TodaySessionData>,
        programId: String?,
        programSlug: String?,
        week: Int,
        day: Int
    ) {
        binding.cardHero.setCardBackgroundColor(requireContext().getColor(R.color.surface))
        binding.tvHeroTitle.text = getString(R.string.train_hero_ready_title)
        binding.tvHeroSubtitle.text = getString(R.string.train_hero_ready_subtitle)
        
        binding.layoutMotivation.visibility = View.VISIBLE
        binding.tvMotivation.text = "Consistency is key. You're doing great!"

        val nextSession = sessions.firstOrNull { !it.isCompleted }
        if (nextSession != null) {
            val language = java.util.Locale.getDefault().language
            val sessionName = nextSession.name[language] ?: nextSession.name["en"] ?: "Session"
            binding.btnSmartAction.visibility = View.VISIBLE
            binding.btnSmartAction.text = "Start $sessionName"
            binding.btnSmartAction.setOnClickListener {
                startSession(programSlug, programId, week, day, nextSession.id)
            }
        }
    }

    private fun renderCompletedState() {
        binding.cardHero.setCardBackgroundColor(requireContext().getColor(R.color.surface_variant))
        binding.tvHeroTitle.text = "Great Job!"
        binding.tvHeroSubtitle.text = "You've completed all training for today."
        
        binding.layoutMotivation.visibility = View.GONE
        
        binding.btnSmartAction.visibility = View.VISIBLE
        binding.btnSmartAction.text = "View Today's Report"
        binding.btnSmartAction.setOnClickListener {
            (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_reports)
        }
    }

    private fun renderRestDayState() {
        binding.cardHero.setCardBackgroundColor(requireContext().getColor(R.color.surface_variant))
        binding.tvHeroTitle.text = "Rest Day"
        binding.tvHeroSubtitle.text = "Muscles grow when you rest. Take it easy today."
        
        binding.layoutMotivation.visibility = View.VISIBLE
        binding.tvMotivation.text = "Hydrate well and focus on your nutrition."

        binding.btnSmartAction.visibility = View.GONE
        binding.layoutTodayPlan.visibility = View.GONE
    }

    private fun startSession(programSlug: String?, programId: String?, week: Int, day: Int, sessionId: String) {
        if (programId == null || programSlug == null) return
        val intent = Intent(requireContext(), ProgramSessionActivity::class.java).apply {
            putExtra(ProgramSessionActivity.EXTRA_PROGRAM_SLUG, programSlug)
            putExtra(ProgramSessionActivity.EXTRA_PROGRAM_ID, programId)
            putExtra(ProgramSessionActivity.EXTRA_WEEK_NUMBER, week)
            putExtra(ProgramSessionActivity.EXTRA_DAY_NUMBER, day)
            putExtra(ProgramSessionActivity.EXTRA_TARGET_SESSION_ID, sessionId)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ─────────────────────────────────────────────────────────
    // Adapter
    // ─────────────────────────────────────────────────────────

    inner class InteractiveSessionAdapter(
        private val items: List<TodaySessionData>,
        private val onClick: (TodaySessionData) -> Unit
    ) : RecyclerView.Adapter<InteractiveSessionAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val root: MaterialCardView = view as MaterialCardView
            val tvName: TextView = view.findViewById(R.id.tvSessionName)
            val tvDetails: TextView = view.findViewById(R.id.tvSessionDetails)
            val ivStatus: ImageView = view.findViewById(R.id.ivSessionStatus)
            val layoutScore: LinearLayout = view.findViewById(R.id.layoutSessionScore)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_interactive_session_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = items[position]
            val language = java.util.Locale.getDefault().language
            
            holder.tvName.text = session.name[language] ?: session.name["en"] ?: "Session"
            holder.tvDetails.text = "${session.itemCount} exercises • ${session.estimatedDurationMin ?: "--"} min"

            if (session.isCompleted) {
                holder.ivStatus.visibility = View.VISIBLE
                holder.layoutScore.visibility = View.VISIBLE
                holder.root.alpha = 0.7f
                holder.root.setOnClickListener(null)
            } else {
                holder.ivStatus.visibility = View.GONE
                holder.layoutScore.visibility = View.GONE
                holder.root.alpha = 1.0f
                holder.root.setOnClickListener { onClick(session) }
            }
        }

        override fun getItemCount() = items.size
    }
}