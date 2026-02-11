package com.trainingvalidator.poc.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.network.MetricsResponse

/**
 * ReportsRecordsFragment — Records tab in Reports Hub.
 * Shows: Personal bests, longest streak, program grade.
 */
class ReportsRecordsFragment : Fragment() {

    private var metricsData: MetricsResponse? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_reports_records, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val parentMetrics = (parentFragment as? HistoryFragment)?.getMetrics()
        if (parentMetrics != null) updateData(parentMetrics)
    }

    fun updateData(metrics: MetricsResponse?) {
        metricsData = metrics
        val view = view ?: return
        val summary = metrics?.summary ?: return

        // Best form score — find across all weeks
        var bestFormScore = 0f
        var bestWeekNumber = 0
        val weeks = summary.weeks ?: emptyList()
        for (week in weeks) {
            if (week.averageFormScore > bestFormScore) {
                bestFormScore = week.averageFormScore
                bestWeekNumber = week.weekNumber
            }
        }

        view.findViewById<TextView>(R.id.tvBestFormScore).text =
            "${bestFormScore.toInt()}%"
        view.findViewById<TextView>(R.id.tvBestFormScoreContext).text =
            getString(R.string.reports_week_format, bestWeekNumber)

        // Longest streak
        view.findViewById<TextView>(R.id.tvLongestStreak).text =
            (summary.currentStreak ?: 0).toString()

        // Most reps — find highest single-session reps
        var mostReps = 0
        for (week in weeks) {
            for (day in week.days ?: emptyList()) {
                for (session in day.sessions ?: emptyList()) {
                    if (session.totalReps > mostReps) {
                        mostReps = session.totalReps
                    }
                }
            }
        }
        view.findViewById<TextView>(R.id.tvMostReps).text = mostReps.toString()

        // Program grade
        view.findViewById<TextView>(R.id.tvProgramGrade).text =
            summary.programGrade ?: "-"
    }
}
