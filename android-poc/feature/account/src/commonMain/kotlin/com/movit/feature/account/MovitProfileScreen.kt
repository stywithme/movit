package com.movit.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonSize
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitEmptyState
import com.movit.designsystem.components.MovitErrorState
import com.movit.designsystem.components.MovitListCard
import com.movit.designsystem.components.MovitListRow
import com.movit.designsystem.components.MovitLoadingState
import com.movit.designsystem.components.MovitScaffold
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@Composable
fun MovitProfileScreen(
    state: MovitProfileUiState,
    onEvent: (MovitProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    MovitScaffold(
        modifier = modifier,
        title = movitText("profile_title"),
        subtitle = movitText("profile_subtitle"),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
        ) {
            when {
                state.isLoading -> MovitLoadingState(message = movitText("profile_loading"))
                !state.isSignedIn -> {
                    MovitEmptyState(
                        title = movitText("profile_sign_in_prompt"),
                        message = movitText("profile_sign_in_sub"),
                        actionLabel = movitText("auth_sign_in"),
                        onActionClick = { onEvent(MovitProfileEvent.SignInClicked) },
                    )
                }
                state.errorMessage != null -> {
                    MovitErrorState(
                        message = state.errorMessage,
                        onRetry = { onEvent(MovitProfileEvent.RetryClicked) },
                    )
                }
                state.profile != null -> {
                    ProfileContent(
                        profile = state.profile,
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileContent(
    profile: ProfileUi,
    onEvent: (MovitProfileEvent) -> Unit,
) {
    ProfileHero(profile = profile)
    ProCard(profile = profile, onEvent = onEvent)
    SettingsGroup(title = movitText("profile_preferences")) {
        MovitListRow(
            title = movitText("profile_language"),
            trailingValue = profile.language,
            onClick = null,
        )
        MovitListRow(
            title = movitText("profile_appearance"),
            subtitle = movitText("profile_appearance_sub"),
            trailingValue = profile.appearance,
            onClick = null,
        )
        MovitListRow(
            title = movitText("profile_audio_cues"),
            showChevron = false,
            trailing = {
                Switch(
                    checked = profile.audioCuesEnabled,
                    onCheckedChange = { onEvent(MovitProfileEvent.AudioCuesChanged(it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
        )
        MovitListRow(
            title = movitText("profile_haptic"),
            showChevron = false,
            trailing = {
                Switch(
                    checked = profile.hapticEnabled,
                    onCheckedChange = { },
                    enabled = false,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
        )
    }
    SettingsGroup(title = movitText("profile_training")) {
        MovitListRow(
            title = movitText("profile_training_profile"),
            subtitle = profile.trainingProfileSummary,
            onClick = { onEvent(MovitProfileEvent.TrainingProfileClicked) },
        )
        MovitListRow(
            title = movitText("profile_body_assessment"),
            subtitle = movitText("profile_body_assessment_sub"),
            onClick = { onEvent(MovitProfileEvent.AssessmentClicked) },
        )
        MovitListRow(
            title = movitText("profile_level_plan"),
            subtitle = movitText("profile_level_plan_sub"),
            onClick = { onEvent(MovitProfileEvent.LevelClicked) },
        )
    }
    SettingsGroup(title = movitText("profile_support")) {
        MovitListRow(
            title = movitText("profile_sign_out"),
            showChevron = false,
            onClick = { onEvent(MovitProfileEvent.LogoutClicked) },
        )
    }
}

@Composable
private fun ProfileHero(profile: ProfileUi) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = profile.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.W800,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        Text(
            text = profile.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.movitColors.textSecondary,
        )
    }
}

@Composable
private fun ProCard(
    profile: ProfileUi,
    onEvent: (MovitProfileEvent) -> Unit,
) {
    MovitCard(variant = MovitCardVariant.Filled) {
        if (profile.isPro) {
            Text(
                text = movitText("profile_pro_badge"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.W800,
                color = MaterialTheme.movitColors.limeDeep,
            )
            Text(
                text = profile.subscriptionLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.W800,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
            Text(
                text = profile.subscriptionRenewal.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.movitColors.textSecondary,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
            MovitButton(
                text = movitText("profile_manage_subscription"),
                onClick = { onEvent(MovitProfileEvent.ManageSubscriptionClicked) },
                variant = MovitButtonVariant.Outlined,
                size = MovitButtonSize.Small,
                modifier = Modifier.padding(top = MovitSpacing.md),
            )
        } else {
            Text(
                text = movitText("profile_unlock_pro"),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.W800,
            )
            Text(
                text = movitText("profile_unlock_pro_sub"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.movitColors.textSecondary,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
            MovitButton(
                text = movitText("profile_view_plans"),
                onClick = { onEvent(MovitProfileEvent.ViewPlansClicked) },
                variant = MovitButtonVariant.Tonal,
                size = MovitButtonSize.Small,
                modifier = Modifier.padding(top = MovitSpacing.md),
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.W800,
            color = MaterialTheme.movitColors.textTertiary,
            modifier = Modifier.padding(start = MovitSpacing.xs),
        )
        MovitListCard { content() }
    }
}
