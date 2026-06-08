package com.movit.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovitScaffold(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MovitTopBar(
                title = title,
                subtitle = subtitle,
                actions = actions,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}
