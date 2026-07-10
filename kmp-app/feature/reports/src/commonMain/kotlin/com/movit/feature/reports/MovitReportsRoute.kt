package com.movit.feature.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.movit.resources.LocalMovitLanguage
import com.movit.resources.localizedString
import kotlinx.coroutines.launch

@Composable
fun MovitReportsRoute(
    modifier: Modifier = Modifier,
    viewModel: MovitReportsViewModel = viewModel { MovitReportsViewModel() },
    userName: String = "Athlete",
    onProfileClick: () -> Unit = {},
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
        onProfileClick = onProfileClick,
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
    val language = LocalMovitLanguage.current
    val shareAction = rememberReportShareAction()

    LaunchedEffect(viewModel) {
        viewModel.loadInitial()
    }

    if (com.movit.core.data.MovitData.isInstalled) {
        LaunchedEffect(viewModel) {
            com.movit.core.data.MovitData.sync.cacheInvalidated.collect {
                viewModel.load()
            }
        }
    }

    LaunchedEffect(viewModel, language, shareAction) {
        viewModel.effects.collect { effect ->
            val payload = when (effect) {
                is ReportDetailEffect.ShareRequested -> effect.payload
                is ReportDetailEffect.ExportRequested -> effect.payload
            }
            val chooserTitle = localizedString(language, payload.chooserTitleKey)
            if (!shareAction(payload.text, chooserTitle)) {
                onEffect(effect)
            }
        }
    }

    ReportDetailScreen(
        state = state,
        onBack = onBack,
        onPageSelected = { viewModel.onEvent(ReportDetailEvent.PageSelected(it)) },
        onShare = { viewModel.onEvent(ReportDetailEvent.ShareClicked) },
        onExport = { viewModel.onEvent(ReportDetailEvent.ExportClicked) },
        onRetry = { scope.launch { viewModel.load() } },
        modifier = modifier,
    )
}
