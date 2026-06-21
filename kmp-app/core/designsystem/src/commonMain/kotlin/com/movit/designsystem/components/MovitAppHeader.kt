package com.movit.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

@Composable
fun MovitAppHeader(
    modifier: Modifier = Modifier,
    variant: MovitHeaderVariant = MovitHeaderVariant.TabPage,
    greeting: String = "",
    userName: String = "",
    pageTitle: String? = null,
    pageSubtitle: String? = null,
    showNotification: Boolean = true,
    hasUnreadNotifications: Boolean = false,
    onNotificationClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    when (variant) {
        MovitHeaderVariant.Home -> HomeAppHeader(
            modifier = modifier,
            greeting = greeting,
            userName = userName,
            pageTitle = pageTitle,
            pageSubtitle = pageSubtitle,
            showNotification = showNotification,
            hasUnreadNotifications = hasUnreadNotifications,
            onNotificationClick = onNotificationClick,
            onProfileClick = onProfileClick,
            actions = actions,
        )
        MovitHeaderVariant.TabPage -> TabPageAppHeader(
            modifier = modifier,
            userName = userName,
            pageTitle = pageTitle,
            pageSubtitle = pageSubtitle,
            onProfileClick = onProfileClick,
            actions = actions,
        )
    }
}

@Composable
private fun HomeAppHeader(
    modifier: Modifier,
    greeting: String,
    userName: String,
    pageTitle: String?,
    pageSubtitle: String?,
    showNotification: Boolean,
    hasUnreadNotifications: Boolean,
    onNotificationClick: (() -> Unit)?,
    onProfileClick: (() -> Unit)?,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = MovitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileAvatarSurface(
            userName = userName,
            onClick = onProfileClick,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = MovitSpacing.md),
        ) {
            if (pageTitle != null) {
                HeaderTitleBlock(pageTitle = pageTitle, pageSubtitle = pageSubtitle)
            } else {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W800,
                )
            }
        }

        actions()

        if (showNotification) {
            NotificationIconButton(
                hasUnreadNotifications = hasUnreadNotifications,
                onNotificationClick = onNotificationClick,
            )
        }
    }
}

@Composable
private fun TabPageAppHeader(
    modifier: Modifier,
    userName: String,
    pageTitle: String?,
    pageSubtitle: String?,
    onProfileClick: (() -> Unit)?,
    actions: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = MovitSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = MovitSpacing.md),
        ) {
            if (pageTitle != null) {
                HeaderTitleBlock(pageTitle = pageTitle, pageSubtitle = pageSubtitle)
            }
        }

        actions()

        if (onProfileClick != null) {
            ProfileAvatarSurface(
                userName = userName,
                onClick = onProfileClick,
            )
        }
    }
}

@Composable
private fun HeaderTitleBlock(
    pageTitle: String,
    pageSubtitle: String?,
) {
    Text(
        text = pageTitle,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.W800,
    )
    if (pageSubtitle != null) {
        Text(
            text = pageSubtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.movitColors.textSecondary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun ProfileAvatarSurface(
    userName: String,
    onClick: (() -> Unit)?,
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            ProfileAvatarInitial(userName = userName)
        }
    } else {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            ProfileAvatarInitial(userName = userName)
        }
    }
}

@Composable
private fun NotificationIconButton(
    hasUnreadNotifications: Boolean,
    onNotificationClick: (() -> Unit)?,
) {
    Box {
        IconButton(onClick = { onNotificationClick?.invoke() }) {
            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
        }
        if (hasUnreadNotifications) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
        }
    }
}

@Composable
private fun ProfileAvatarInitial(userName: String) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = userName.take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W800,
        )
    }
}
