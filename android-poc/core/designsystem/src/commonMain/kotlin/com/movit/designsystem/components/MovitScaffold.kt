package com.movit.designsystem.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Standard page shell: [MovitAppHeader] + body.
 *
 * Use [MovitHeaderVariant.Home] for the Home tab (avatar start, notifications).
 * Use [MovitHeaderVariant.TabPage] for Train / Explore / Reports (title start, avatar end).
 */
@Composable
fun MovitScaffold(
    modifier: Modifier = Modifier,
    headerVariant: MovitHeaderVariant = MovitHeaderVariant.TabPage,
    title: String? = null,
    subtitle: String? = null,
    greeting: String = "",
    userName: String = "",
    showNotification: Boolean = headerVariant == MovitHeaderVariant.Home,
    hasUnreadNotifications: Boolean = false,
    onNotificationClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MovitAppHeader(
                variant = headerVariant,
                greeting = greeting,
                pageTitle = title,
                pageSubtitle = subtitle,
                userName = userName,
                showNotification = showNotification,
                hasUnreadNotifications = hasUnreadNotifications,
                onNotificationClick = onNotificationClick,
                onProfileClick = onProfileClick,
                actions = actions,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        content(
            PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = 0.dp,
            ),
        )
    }
}
