package com.trainingvalidator.poc.ui.main

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.*
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentHistoryBinding
import com.trainingvalidator.poc.network.MetricsResponse
import com.trainingvalidator.poc.network.WeekMetrics
import com.trainingvalidator.poc.storage.ProgramRepository
import com.trainingvalidator.poc.storage.ReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * HistoryFragment — Reimplemented as the Reports Hub.
 *
 * 4 Tabs: Overview | Exercises | Trends | Records
 * Data comes from the unified reports endpoint via ReportRepository.
 */
class HistoryFragment : Fragment() {

    companion object {
        private const val TAG = "ReportsHub"
    }

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private var metrics: MetricsResponse? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupTabs()
        loadData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupTabs() {
        val tabTitles = listOf(
            getString(R.string.reports_tab_overview),
            getString(R.string.reports_tab_exercises),
            getString(R.string.reports_tab_trends),
            getString(R.string.reports_tab_records)
        )

        val adapter = ReportsTabAdapter(this)
        binding.viewPager.adapter = adapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    private fun loadData() {
        lifecycleScope.launch {
            val programRepo = ProgramRepository.getInstance(requireContext())
            withContext(Dispatchers.IO) { programRepo.initialize() }

            val activeProgram = programRepo.getActiveProgram()
            if (activeProgram == null) {
                Log.d(TAG, "No active program — showing empty state")
                return@launch
            }

            val reportRepo = ReportRepository.getInstance(requireContext())
            val result = withContext(Dispatchers.IO) {
                reportRepo.getProgramMetrics(activeProgram.id, includeChildren = true)
            }

            if (_binding == null) return@launch

            if (result != null && result.success) {
                metrics = result
                // Notify child fragments of data update
                updateChildFragments()
            }
        }
    }

    fun getMetrics(): MetricsResponse? = metrics

    private fun updateChildFragments() {
        val adapter = binding.viewPager.adapter as? ReportsTabAdapter ?: return
        adapter.notifyDataChanged(metrics)
    }

    // ─── Tab Adapter ───

    class ReportsTabAdapter(fragment: Fragment) :
        androidx.viewpager2.adapter.FragmentStateAdapter(fragment) {

        private val fragments = mutableListOf<Fragment>()

        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            val f = when (position) {
                0 -> ReportsOverviewFragment()
                1 -> ReportsExercisesFragment()
                2 -> ReportsTrendsFragment()
                3 -> ReportsRecordsFragment()
                else -> ReportsOverviewFragment()
            }
            fragments.add(f)
            return f
        }

        fun notifyDataChanged(metrics: MetricsResponse?) {
            for (f in fragments) {
                when (f) {
                    is ReportsOverviewFragment -> f.updateData(metrics)
                    is ReportsExercisesFragment -> f.updateData(metrics)
                    is ReportsTrendsFragment -> f.updateData(metrics)
                    is ReportsRecordsFragment -> f.updateData(metrics)
                }
            }
        }
    }
}
