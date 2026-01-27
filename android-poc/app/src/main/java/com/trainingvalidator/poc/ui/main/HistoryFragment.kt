package com.trainingvalidator.poc.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentHistoryBinding

/**
 * HistoryFragment - Workout history with stats
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    
    private val historyItems = mutableListOf<HistoryEntry>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        loadMockData()
    }

    private fun setupUI() {
        binding.rvHistory.layoutManager = LinearLayoutManager(requireContext())
        binding.rvHistory.adapter = HistoryAdapter(historyItems)
    }

    private fun loadMockData() {
        binding.tvWeeklyAverage.text = getString(R.string.sample_weekly_average)
        binding.tvTotalTime.text = getString(R.string.sample_total_time)

        // Mock history data
        historyItems.addAll(listOf(
            HistoryEntry(
                getString(R.string.sample_exercise_squats),
                getString(R.string.sample_date_today, "10:30 AM"),
                92
            ),
            HistoryEntry(
                getString(R.string.sample_exercise_pushups),
                getString(R.string.sample_date_today, "9:15 AM"),
                88
            ),
            HistoryEntry(
                getString(R.string.sample_exercise_deadlifts),
                getString(R.string.sample_date_yesterday, "6:00 PM"),
                75
            )
        ))
        
        binding.rvHistory.adapter?.notifyDataSetChanged()
        
        if (historyItems.isEmpty()) {
            binding.layoutEmpty.visibility = View.VISIBLE
        } else {
            binding.layoutEmpty.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    data class HistoryEntry(
        val exerciseName: String,
        val date: String,
        val score: Int
    )

    inner class HistoryAdapter(
        private val items: List<HistoryEntry>
    ) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val card: CardView = view.findViewById(R.id.cardHistory)
            val tvName: TextView = view.findViewById(R.id.tvExerciseName)
            val tvDate: TextView = view.findViewById(R.id.tvDate)
            val tvScore: TextView = view.findViewById(R.id.tvScore)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_history_entry, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            
            holder.tvName.text = item.exerciseName
            holder.tvDate.text = item.date
            holder.tvScore.text = "${item.score}%"
            
            // Color based on score
            val scoreColor = when {
                item.score >= 85 -> R.color.success
                item.score >= 70 -> R.color.warning
                else -> R.color.error
            }
            holder.tvScore.setTextColor(resources.getColor(scoreColor, null))
        }

        override fun getItemCount() = items.size
    }
}
