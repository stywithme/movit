package com.movit.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing

enum class MovitCardVariant {
    Filled,
    Outlined,
}

@Composable
fun MovitCard(
    modifier: Modifier = Modifier,
    variant: MovitCardVariant = MovitCardVariant.Filled,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cardModifier = modifier.alpha(if (enabled) 1f else 0.5f)
    val innerModifier = Modifier.padding(MovitSpacing.lg)
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)

    when (variant) {
        MovitCardVariant.Filled -> Card(
            modifier = cardModifier,
            shape = MaterialTheme.shapes.large,
            colors = colors,
            elevation = elevation,
        ) {
            Column(modifier = innerModifier, content = content)
        }

        MovitCardVariant.Outlined -> OutlinedCard(
            modifier = cardModifier,
            shape = MaterialTheme.shapes.large,
            colors = colors,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            elevation = elevation,
        ) {
            Column(modifier = innerModifier, content = content)
        }
    }
}
