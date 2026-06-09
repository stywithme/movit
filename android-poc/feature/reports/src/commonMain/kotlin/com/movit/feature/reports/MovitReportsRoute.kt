package com.movit.feature.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@Composable
fun MovitReportsRoute(
    modifier: Modifier = Modifier,
    viewModel: MovitReportsViewModel = viewModel { MovitReportsViewModel() },
    userName: String = "Athlete",
    onEffect: (MovitReportsEffect) -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            onEffect(effect)
        }
    }

    MovitReportsScreen(
        state = state,
        onEvent = { event ->
            when (event) {
                MovitReportsEvent.RetryClicked -> scope.launch { viewModel.load(isRefresh = false) }
                MovitReportsEvent.RefreshRequested -> scope.launch { viewModel.load(isRefresh = true) }
                else -> viewModel.onEvent(event)
            }
        },
        modifier = modifier,
        userName = userName,
    )
}

@Composable
fun ReportDetailRoute(
    reportId: String,
    onBack: () -> Unit,
    onEffect: (ReportDetailEffect) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ReportDetailViewModel = viewModel { ReportDetailViewModel(reportId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ReportDetailEffect.ShareRequested,
                ReportDetailEffect.ExportRequested,
                -> onEffect(effect)
            }
        }
    }

    ReportDetailScreen(
        state = state,
        onBack = onBack,
        onPageSelected = viewModel::onPageSelected,
        onShare = viewModel::onShareClicked,
        onExport = viewModel::onExportClicked,
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}
