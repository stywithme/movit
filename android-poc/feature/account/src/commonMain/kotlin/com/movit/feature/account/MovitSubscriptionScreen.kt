package com.movit.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitListRow
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun MovitSubscriptionScreen(
    isPro: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(MovitSpacing.lg),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = movitText("profile_back"),
                )
            }
            Text(
                text = movitText("profile_subscription"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.W800,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        MovitCard(modifier = Modifier.padding(top = MovitSpacing.lg)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = movitText("profile_sub_best_value"),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.W800,
                    color = MaterialTheme.movitColors.limeDeep,
                )
                Text(
                    text = movitText("profile_sub_price"),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.W800,
                    modifier = Modifier.padding(top = MovitSpacing.sm),
                )
                Text(
                    text = movitText("profile_sub_per_month"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MovitSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(MovitSpacing.xs),
                ) {
                    listOf(
                        movitText("profile_sub_feature_reports"),
                        movitText("profile_sub_feature_insights"),
                        movitText("profile_sub_feature_scans"),
                        movitText("profile_sub_feature_support"),
                    ).forEach { feature ->
                        Text(
                            text = "• $feature",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.movitColors.textSecondary,
                        )
                    }
                }
                MovitButton(
                    text = if (isPro) {
                        movitText("profile_manage_subscription")
                    } else {
                        movitText("profile_subscribe_now")
                    },
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = MovitSpacing.lg),
                )
                MovitButton(
                    text = movitText("profile_restore_purchases"),
                    onClick = onBack,
                    variant = MovitButtonVariant.Text,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        MovitCard(modifier = Modifier.padding(top = MovitSpacing.md)) {
            MovitListRow(
                title = movitText("profile_sub_annual"),
                subtitle = movitText("profile_sub_annual_sub"),
                onClick = onBack,
            )
        }
    }
}
