package com.movit.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.movit.designsystem.movitColors
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors

@Composable
fun MovitSkeletonBox(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(MovitRadius.md))
            .background(MaterialTheme.movitColors.surface2),
    )
}

@Composable
fun MovitSkeletonCard(modifier: Modifier = Modifier) {
    MovitCard(modifier = modifier, variant = MovitCardVariant.Elevated) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            MovitSkeletonBox(modifier = Modifier.size(46.dp))
            Column(modifier = Modifier.padding(start = MovitSpacing.md).weight(1f)) {
                MovitSkeletonBox(modifier = Modifier.fillMaxWidth(0.6f).height(14.dp))
                MovitSkeletonBox(
                    modifier = Modifier
                        .padding(top = MovitSpacing.sm)
                        .fillMaxWidth(0.4f)
                        .height(11.dp),
                )
            }
        }
        MovitSkeletonBox(
            modifier = Modifier
                .padding(top = MovitSpacing.lg)
                .fillMaxWidth()
                .height(96.dp),
        )
    }
}
