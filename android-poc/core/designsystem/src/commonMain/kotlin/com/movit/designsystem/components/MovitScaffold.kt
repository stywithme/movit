package com.movit.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Standard page shell: [MovitAppHeader] (avatar · title/subtitle · notifications) + body.
 */
@Composable
fun MovitScaffold(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    userName: String = "Mahmoud",
    showNotification: Boolean = true,
    onNotificationClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MovitAppHeader(
                pageTitle = title,
                pageSubtitle = subtitle,
                userName = userName,
                showNotification = showNotification,
                onNotificationClick = onNotificationClick,
                onProfileClick = onProfileClick,
                actions = actions,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}
