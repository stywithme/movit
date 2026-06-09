package com.movit.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import com.movit.resources.movitText
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

enum class MovitNavDestination {
    Home,
    Train,
    Explore,
    Reports,
    Profile,
    /** Debug-only — design system catalog previews. */
    Components,
}

@Composable
fun MovitFloatingNavBar(
    selected: MovitNavDestination,
    onDestinationSelected: (MovitNavDestination) -> Unit,
    modifier: Modifier = Modifier,
    destinations: List<MovitNavDestination> = listOf(
        MovitNavDestination.Home,
        MovitNavDestination.Train,
        MovitNavDestination.Explore,
        MovitNavDestination.Reports,
    ),
) {
    val movit = MaterialTheme.movitColors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = MovitSpacing.xl)
            .height(64.dp),
        shape = RoundedCornerShape(MovitRadius.full),
        color = movit.ink,
        contentColor = movit.onInk,
        shadowElevation = 16.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MovitSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            destinations.forEach { destination ->
                val isSelected = destination == selected
                if (isSelected) {
                    Surface(
                        onClick = { onDestinationSelected(destination) },
                        shape = RoundedCornerShape(MovitRadius.full),
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Row(
                            modifier = Modifier
                                .height(44.dp)
                                .padding(horizontal = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Icon(
                                imageVector = destination.icon(),
                                contentDescription = destination.label(),
                                modifier = Modifier.height(22.dp),
                            )
                            Text(
                                text = destination.label(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.W700,
                            )
                        }
                    }
                } else {
                    Surface(
                        onClick = { onDestinationSelected(destination) },
                        shape = RoundedCornerShape(MovitRadius.full),
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = movit.onInkVeil55,
                    ) {
                        Row(
                            modifier = Modifier
                                .height(44.dp)
                                .padding(horizontal = MovitSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = destination.icon(),
                                contentDescription = destination.label(),
                                modifier = Modifier.height(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MovitNavDestination.label(): String = when (this) {
    MovitNavDestination.Home -> movitText("nav_home")
    MovitNavDestination.Train -> movitText("nav_train")
    MovitNavDestination.Explore -> movitText("nav_explore")
    MovitNavDestination.Reports -> movitText("nav_reports")
    MovitNavDestination.Profile -> movitText("nav_account")
    MovitNavDestination.Components -> "Components"
}

private fun MovitNavDestination.icon(): ImageVector = when (this) {
    MovitNavDestination.Home -> Icons.Default.Home
    MovitNavDestination.Train -> Icons.Default.FitnessCenter
    MovitNavDestination.Explore -> Icons.Default.Explore
    MovitNavDestination.Reports -> Icons.Default.Assessment
    MovitNavDestination.Profile -> Icons.Default.Person
    MovitNavDestination.Components -> Icons.Default.Apps
}
