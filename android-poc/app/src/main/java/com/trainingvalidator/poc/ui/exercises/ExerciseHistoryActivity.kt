package com.trainingvalidator.poc.ui.exercises

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.ActivityExerciseHistoryBinding
import com.trainingvalidator.poc.network.SetMetrics
import com.trainingvalidator.poc.storage.ReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ExerciseHistoryActivity â€” Detailed exercise history with performance charts.
 * Uses the unified reports endpoint with scope=exercise.
 */
class ExerciseHistoryActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ExerciseHistory"
        const val EXTRA_PROGRAM_ID = "program_id"
        const val EXTRA_EXERCISE_SLUG = "exercise_slug"
        const val EXTRA_EXERCISE_NAME = "exercise_name"
    }

    private lateinit var binding: ActivityExerciseHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExerciseHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val exerciseName = intent.getStringExtra(EXTRA_EXERCISE_NAME) ?: "Exercise"
        binding.tvExerciseName.text = exerciseName
        binding.btnBack.setOnClickListener { finish() }
        binding.rvSets.layoutManager = LinearLayoutManager(this)

        loadData()
    }

    private fun loadData() {
        val programId = intent.getStringExtra(EXTRA_PROGRAM_ID) ?: return
        val exerciseSlug = intent.getStringExtra(EXTRA_EXERCISE_SLUG) ?: return

        lifecycleScope.launch {
            try {
                val reportRepo = ReportRepository.getInstance(this@ExerciseHistoryActivity)
                val metrics = withContext(Dispatchers.IO) {
                    reportRepo.getExerciseMetrics(programId, exerciseSlug, includeHistory = true)
                }

                if (metrics != null && metrics.success) {
                    val summary = metrics.summary

                    // Quick stats
                    binding.tvFormScore.text = "${summary?.averageFormScore?.toInt() ?: 0}%"
                    binding.tvTotalSets.text = (summary?.setsCompleted ?: 0).toString()
                    binding.tvTotalReps.text = (summary?.totalReps ?: 0).toString()

                    // Performance chart from sets
                    val sets = summary?.sets ?: emptyList()
                    if (sets.isNotEmpty()) {
                        renderPerformanceChart(sets)
                        binding.rvSets.adapter = SetAdapter(sets)
                    }

                    // Comparison insight
                    val comparison = metrics.comparison
                    if (comparison != null && comparison.formScoreDelta != null) {
                        val delta = comparison.formScoreDelta
                        binding.tvImprovementInsight.text =
                            getString(R.string.workout_report_improved_format, delta)
                        binding.tvImprovementInsight.setTextColor(
                            getColor(if (delta >= 0) R.color.success else R.color.error)
                        )
                        binding.tvImprovementInsight.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load exercise history", e)
            }
        }
    }

    private fun renderPerformanceChart(sets: List<SetMetrics>) {
        val entries = sets.mapIndexed { i, s ->
            Entry(i.toFloat(), s.averageFormScore)
        }

        val dataSet = LineDataSet(entries, "Form Score").apply {
            color = getColor(R.color.primary)
            lineWidth = 2.5f
            setDrawCircles(true)
            circleRadius = 4f
            setCircleColor(getColor(R.color.primary))
            setDrawCircleHole(true)
            circleHoleRadius = 2f
            setDrawValues(true)
            valueTextSize = 10f
            valueTextColor = getColor(R.color.text_secondary)
            setDrawFilled(true)
            fillColor = getColor(R.color.primary)
            fillAlpha = 20
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val chart = binding.chartPerformance
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
                override fun getFormattedValue(value: Float): String {
                    return getString(R.string.reports_set_format, value.toInt() + 1)
                }
            }
            textColor = getColor(R.color.text_hint)
        }

        chart.axisLeft.apply {
            setDrawGridLines(true)
            gridColor = getColor(R.color.surface_border)
            axisMinimum = 0f
            axisMaximum = 100f
            textColor = getColor(R.color.text_hint)
        }

        chart.axisRight.isEnabled = false
        chart.invalidate()
    }

    inner class SetAdapter(
        private val sets: List<SetMetrics>
    ) : RecyclerView.Adapter<SetAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvSetNumber: TextView = view.findViewById(R.id.tvExerciseName)
            val tvMeta: TextView = view.findViewById(R.id.tvExerciseTrainedCount)
            val tvFormScore: TextView = view.findViewById(R.id.tvExerciseFormScore)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_report_exercise_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val set = sets[position]
            holder.tvSetNumber.text = getString(R.string.reports_set_format, set.setNumber)
            holder.tvMeta.text = "${set.totalReps}/${set.repsTarget} reps"
            holder.tvFormScore.text = "${set.averageFormScore.toInt()}%"
        }

        override fun getItemCount() = sets.size
    }
}
