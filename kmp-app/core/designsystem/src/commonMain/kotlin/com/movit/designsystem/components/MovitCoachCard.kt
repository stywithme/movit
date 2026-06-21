package com.movit.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

@Composable
fun MovitCoachCard(
    name: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    avatarLabel: String = name.take(1).uppercase(),
    onMessage: (() -> Unit)? = null,
    onCall: (() -> Unit)? = null,
) {
    val movit = MaterialTheme.movitColors
    MovitCard(
        modifier = modifier.fillMaxWidth(),
        variant = MovitCardVariant.Filled,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(50.dp),
            ) {
                Text(
                    text = avatarLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.W800,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(MovitSpacing.md),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.W700,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = movit.textSecondary,
                )
            }
            if (onMessage != null) {
                IconButton(onClick = onMessage) {
                    Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null)
                }
            }
            if (onCall != null) {
                IconButton(onClick = onCall) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary,
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier.padding(MovitSpacing.sm),
                        )
                    }
                }
            }
        }
    }
}
