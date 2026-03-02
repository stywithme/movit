package com.trainingvalidator.poc.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.network.MetricsResponse
import kotlinx.coroutines.launch

/**
 * ReportsTrendsFragment — Trends tab in Reports Hub.
 * Shows: Improvement rate chart, Attendance chart.
 */
class ReportsTrendsFragment : Fragment() {

    private val hubViewModel: ReportsHubViewModel by activityViewModels()
    private var metricsData: MetricsResponse? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_reports_trends, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                hubViewModel.uiState.collect { state ->
                    if (state is ReportsHubUiState.Success) updateData(state.metrics)
                }
            }
        }
    }

    fun updateData(metrics: MetricsResponse?) {
        metricsData = metrics
        val view = view ?: return
        val summary = metrics?.summary ?: return

        // Improvement rate text
        val rate = summary.improvementRate ?: 0f
        val tvRate = view.findViewById<TextView>(R.id.tvImprovementRate)
        if (rate != 0f) {
            tvRate.text = String.format("%+.1f%% since Week 1", rate)
            tvRate.setTextColor(
                requireContext().getColor(if (rate >= 0) R.color.success else R.color.error)
            )
            tvRate.visibility = View.VISIBLE
        } else {
            tvRate.visibility = View.GONE
        }

        // Improvement chart — form score per week
        renderImprovementChart(view, summary.weeklyFormScores)

        // Attendance chart — days trained per week
        renderAttendanceChart(view, summary.weeks)
    }

    private fun renderImprovementChart(view: View, scores: List<Float>?) {
        val chart = view.findViewById<LineChart>(R.id.chartImprovement)
        if (scores == null || scores.all { it == 0f }) {
            chart.visibility = View.GONE
            return
        }
        chart.visibility = View.VISIBLE

        val entries = scores.mapIndexed { i, v -> Entry(i.toFloat(), v) }
        val dataSet = LineDataSet(entries, "Form Score").apply {
            color = requireContext().getColor(R.color.success)
            lineWidth = 2.5f
            setDrawCircles(true)
            circleRadius = 4f
            setCircleColor(requireContext().getColor(R.color.success))
            setDrawCircleHole(true)
            circleHoleRadius = 2f
            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor = requireContext().getColor(R.color.text_secondary)
            setDrawFilled(true)
            fillColor = requireContext().getColor(R.color.success)
            fillAlpha = 15
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        chart.data = LineData(dataSet)
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setTouchEnabled(true)
        chart.setDrawGridBackground(false)

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = "W${value.toInt() + 1}"
            }
            textColor = requireContext().getColor(R.color.text_hint)
        }

        chart.axisLeft.apply {
            setDrawGridLines(true)
            gridColor = requireContext().getColor(R.color.surface_border)
            axisMinimum = 0f
            axisMaximum = 100f
            textColor = requireContext().getColor(R.color.text_hint)
        }

        chart.axisRight.isEnabled = false
        chart.invalidate()
    }

    private fun renderAttendanceChart(
        view: View,
        weeks: List<com.trainingvalidator.poc.network.WeekMetrics>?
    ) {
        val chart = view.findViewById<BarChart>(R.id.chartAttendance)
        if (weeks.isNullOrEmpty()) {
            chart.visibility = View.GONE
            return
        }
        chart.visibility = View.VISIBLE

        val entries = weeks.mapIndexed { i, w ->
            BarEntry(i.toFloat(), w.daysTrained.toFloat())
        }

        val dataSet = BarDataSet(entries, "Days Trained").apply {
            color = requireContext().getColor(R.color.primary)
            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor = requireContext().getColor(R.color.text_secondary)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = value.toInt().toString()
            }
        }

        chart.data = BarData(dataSet).apply { barWidth = 0.5f }
        chart.description.isEnabled = false
        chart.legend.isEnabled = false
        chart.setTouchEnabled(false)
        chart.setDrawGridBackground(false)

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            granularity = 1f
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float) = "W${value.toInt() + 1}"
            }
            textColor = requireContext().getColor(R.color.text_hint)
        }

        chart.axisLeft.apply {
            setDrawGridLines(false)
            axisMinimum = 0f
            textColor = requireContext().getColor(R.color.text_hint)
        }

        chart.axisRight.isEnabled = false
        chart.invalidate()
    }
}
