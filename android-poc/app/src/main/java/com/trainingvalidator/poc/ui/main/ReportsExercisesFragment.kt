package com.trainingvalidator.poc.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.network.MetricsResponse
import com.trainingvalidator.poc.network.WeekMetrics

/**
 * ReportsExercisesFragment — Exercises tab in Reports Hub.
 * Shows a list of all exercises the user has trained with form scores.
 */
class ReportsExercisesFragment : Fragment() {

    private var metricsData: MetricsResponse? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_reports_exercises, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val parentMetrics = (parentFragment as? HistoryFragment)?.getMetrics()
        if (parentMetrics != null) updateData(parentMetrics)
    }

    fun updateData(metrics: MetricsResponse?) {
        metricsData = metrics
        val view = view ?: return
        val rv = view.findViewById<RecyclerView>(R.id.rvExercises)
        rv.layoutManager = LinearLayoutManager(requireContext())

        // 1. Try to use backend-aggregated exercises list (preferred)
        val backendExercises = metrics?.summary?.exercises
        
        val exerciseList: List<ExerciseAggregate> = if (!backendExercises.isNullOrEmpty()) {
            backendExercises.map {
                ExerciseAggregate(
                    slug = it.exerciseSlug,
                    name = it.exerciseName,
                    timesTrainedCount = it.sessionsCount ?: 0,
                    averageScore = it.averageFormScore
                )
            }
        } else {
            // 2. Fallback: Client-side aggregation (for offline or legacy data)
            val exerciseMap = mutableMapOf<String, MutableAggregate>()

            val weeks = metrics?.summary?.weeks ?: emptyList()
            for (week in weeks) {
                val days = week.days ?: continue
                for (day in days) {
                    val sessions = day.sessions ?: continue
                    for (session in sessions) {
                        val exercises = session.exercises ?: continue
                        for (ex in exercises) {
                            val existing = exerciseMap.getOrPut(ex.exerciseSlug) {
                                MutableAggregate(ex.exerciseSlug, ex.exerciseName)
                            }
                            existing.count++
                            existing.totalScore += ex.averageFormScore
                        }
                    }
                }
            }
            
            exerciseMap.values.map {
                ExerciseAggregate(
                    slug = it.slug,
                    name = it.name,
                    timesTrainedCount = it.count,
                    averageScore = if (it.count > 0) it.totalScore / it.count else 0f
                )
            }
        }

        val sortedList = exerciseList.sortedByDescending { it.averageScore }
        rv.adapter = ExerciseAdapter(sortedList)
    }

    // Immutable view model for the adapter
    data class ExerciseAggregate(
        val slug: String,
        val name: String,
        val timesTrainedCount: Int,
        val averageScore: Float
    )

    // Helper for client-side accumulation
    private class MutableAggregate(
        val slug: String,
        val name: String,
        var count: Int = 0,
        var totalScore: Float = 0f
    )

    inner class ExerciseAdapter(
        private val items: List<ExerciseAggregate>
    ) : RecyclerView.Adapter<ExerciseAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvExerciseName)
            val tvCount: TextView = view.findViewById(R.id.tvExerciseTrainedCount)
            val tvFormScore: TextView = view.findViewById(R.id.tvExerciseFormScore)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_report_exercise_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.tvName.text = item.name
            holder.tvCount.text = getString(R.string.reports_times_trained_format, item.timesTrainedCount)
            holder.tvFormScore.text = getString(R.string.reports_form_score_format, item.averageScore.toInt())
        }

        override fun getItemCount() = items.size
    }
}
