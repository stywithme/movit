package com.movit.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing

/**
 * App chrome matching HTML prototypes: header + scroll body + floating bottom nav.
 */
@Composable
fun MovitAppFrame(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { header() },
        bottomBar = {
            Box(
                modifier = Modifier.padding(bottom = MovitSpacing.lg),
                contentAlignment = Alignment.Center,
            ) {
                bottomBar()
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding(),
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MovitSpacing.lg)
                .padding(top = 4.dp, bottom = MovitSpacing.md),
        ) {
            content()
        }
    }
}
