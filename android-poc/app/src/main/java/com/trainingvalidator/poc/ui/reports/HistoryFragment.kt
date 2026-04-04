package com.trainingvalidator.poc.ui.reports

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentHistoryBinding
import kotlinx.coroutines.launch

/**
 * HistoryFragment — Reports Hub with 4 tabs.
 *
 * Uses [ReportsHubViewModel] (shared with child fragments via activityViewModels)
 * to load metrics once and let each tab fragment observe independently.
 */
class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    // Shared with child tab fragments
    private val viewModel: ReportsHubViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupSwipeRefresh()
        setupTabs()
        observeLoadingState()
        viewModel.loadMetrics()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadMetrics()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupTabs() {
        binding.viewPager.adapter = ReportsTabAdapter(this)
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.reports_tab_overview)
                1 -> getString(R.string.reports_tab_exercises)
                2 -> getString(R.string.reports_tab_trends)
                3 -> getString(R.string.reports_tab_records)
                else -> ""
            }
        }.attach()
    }

    private fun observeLoadingState() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (_binding != null && state !is ReportsHubUiState.Loading) {
                        binding.swipeRefresh.isRefreshing = false
                    }
                }
            }
        }
    }
}

// ─── Tab Adapter ─────────────────────────────────────────────────────────────

private class ReportsTabAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount() = 4
    override fun createFragment(position: Int): Fragment = when (position) {
        0 -> ReportsOverviewFragment()
        1 -> ReportsExercisesFragment()
        2 -> ReportsTrendsFragment()
        3 -> ReportsRecordsFragment()
        else -> ReportsOverviewFragment()
    }
}
