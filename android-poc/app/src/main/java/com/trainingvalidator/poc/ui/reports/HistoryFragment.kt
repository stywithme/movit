package com.trainingvalidator.poc.ui.reports

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.tabs.TabLayoutMediator
import com.trainingvalidator.poc.R
import com.trainingvalidator.poc.databinding.FragmentHistoryBinding
import com.trainingvalidator.poc.storage.AuthManager
import com.trainingvalidator.poc.ui.main.MainContainerActivity
import com.trainingvalidator.poc.ui.profile.ProfileActivity
import com.trainingvalidator.poc.ui.subscription.SubscriptionActivity
import com.trainingvalidator.poc.ui.utils.bindUserAvatar
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
    private var hasVisibleReportContent = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupHeaderAvatar()
        setupSwipeRefresh()
        setupTabs()
        observeLoadingState()
        viewModel.loadMetrics()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) bindHeaderAvatar()
    }

    private fun setupHeaderAvatar() {
        binding.ivAvatar.setOnClickListener {
            startActivity(Intent(requireContext(), ProfileActivity::class.java))
        }
        bindHeaderAvatar()
    }

    private fun bindHeaderAvatar() {
        binding.ivAvatar.bindUserAvatar(AuthManager.getAvatarUrl(requireContext()))
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
                    if (_binding != null) renderHubState(state)
                }
            }
        }
    }

    private fun renderHubState(state: ReportsHubUiState) {
        binding.swipeRefresh.isRefreshing = state is ReportsHubUiState.Loading && hasVisibleReportContent

        when (state) {
            ReportsHubUiState.Idle -> Unit
            ReportsHubUiState.Loading -> {
                if (!hasVisibleReportContent) {
                    showState(
                        title = getString(R.string.reports_state_loading_title),
                        message = getString(R.string.reports_state_loading_message),
                        showProgress = true
                    )
                }
            }
            is ReportsHubUiState.Success -> {
                hasVisibleReportContent = true
                binding.layoutReportsState.isVisible = false
                binding.tabLayout.isVisible = true
                binding.viewPager.isVisible = true
            }
            ReportsHubUiState.Empty -> {
                hasVisibleReportContent = false
                showState(
                    title = getString(R.string.reports_state_empty_title),
                    message = getString(R.string.reports_state_empty_message),
                    actionText = getString(R.string.reports_state_start_training),
                    action = ::openTrainingTab
                )
            }
            ReportsHubUiState.NoActiveProgram -> {
                hasVisibleReportContent = false
                showState(
                    title = getString(R.string.reports_state_no_program_title),
                    message = getString(R.string.reports_state_no_program_message),
                    actionText = getString(R.string.reports_state_start_training),
                    action = ::openTrainingTab
                )
            }
            ReportsHubUiState.Locked -> {
                hasVisibleReportContent = false
                showState(
                    title = getString(R.string.reports_state_locked_title),
                    message = getString(R.string.reports_state_locked_message),
                    actionText = getString(R.string.reports_state_upgrade),
                    action = ::openSubscription
                )
            }
            is ReportsHubUiState.Error -> {
                hasVisibleReportContent = false
                showState(
                    title = getString(R.string.reports_state_error_title),
                    message = getString(R.string.reports_state_error_message),
                    actionText = getString(R.string.reports_state_retry),
                    action = viewModel::loadMetrics
                )
            }
        }
    }

    private fun showState(
        title: String,
        message: String,
        showProgress: Boolean = false,
        actionText: String? = null,
        action: (() -> Unit)? = null
    ) {
        binding.tabLayout.isVisible = false
        binding.viewPager.isVisible = false
        binding.layoutReportsState.isVisible = true
        binding.progressReportsState.isVisible = showProgress
        binding.tvReportsStateTitle.text = title
        binding.tvReportsStateMessage.text = message
        binding.btnReportsStateAction.isVisible = actionText != null && action != null
        if (actionText != null && action != null) {
            binding.btnReportsStateAction.text = actionText
            binding.btnReportsStateAction.setOnClickListener { action() }
        }
    }

    private fun openTrainingTab() {
        (activity as? MainContainerActivity)?.navigateToTab(R.id.nav_train)
    }

    private fun openSubscription() {
        startActivity(Intent(requireContext(), SubscriptionActivity::class.java))
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
