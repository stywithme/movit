package com.movit.feature.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.movit.designsystem.components.MovitRemoteImage
import com.movit.designsystem.components.MovitScaffold
import com.movit.designsystem.components.MovitTag
import com.movit.designsystem.components.MovitTagVariant
import com.movit.designsystem.movitColors
import com.movit.resources.movitText
import com.movit.shared.PlatformInfo

@Composable
fun MovitProfileScreen(
    state: MovitProfileUiState,
    onEvent: (MovitProfileEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.activePicker) {
        ProfilePicker.Language -> {
            val profile = state.profile
            if (profile != null) {
                ProfileLanguagePickerDialog(
                    selectedLanguageCode = profile.languageCode,
                    onLanguageSelected = { onEvent(MovitProfileEvent.LanguageSelected(it)) },
                    onDismiss = { onEvent(MovitProfileEvent.PickerDismissed) },
                )
            }
        }
        ProfilePicker.Appearance -> {
            val profile = state.profile
            if (profile != null) {
                ProfileAppearancePickerDialog(
                    selectedThemeMode = profile.themeMode,
                    onThemeModeSelected = { onEvent(MovitProfileEvent.AppearanceSelected(it)) },
                    onDismiss = { onEvent(MovitProfileEvent.PickerDismissed) },
                )
            }
        }
        ProfilePicker.LogoutConfirm -> {
            ProfileLogoutConfirmDialog(
                onConfirm = { onEvent(MovitProfileEvent.LogoutConfirmed) },
                onDismiss = { onEvent(MovitProfileEvent.LogoutDismissed) },
            )
        }
        null -> Unit
    }

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
                        title = movitText("common_error_title"),
                        message = state.errorMessage,
                        actionLabel = movitText("common_retry"),
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
    ProfileHero(profile = profile, onEvent = onEvent)
    ProCard(profile = profile, onEvent = onEvent)
    val languageRowA11y = movitText(
        "profile_setting_row_a11y",
        movitText("profile_language"),
        profileLanguageLabel(profile.languageCode),
    )
    val audioCuesA11y = movitText(
        "profile_toggle_a11y",
        movitText("profile_audio_cues"),
        if (profile.audioCuesEnabled) {
            movitText("onboarding_selected_state_a11y")
        } else {
            movitText("onboarding_unselected_state_a11y")
        },
    )
    val hapticA11y = movitText(
        "profile_toggle_a11y",
        movitText("profile_haptic"),
        if (profile.hapticEnabled) {
            movitText("onboarding_selected_state_a11y")
        } else {
            movitText("onboarding_unselected_state_a11y")
        },
    )
    val trainingProfileA11y = movitText("profile_training_profile_a11y")

    SettingsGroup(title = movitText("profile_preferences")) {
        MovitListRow(
            title = movitText("profile_language"),
            trailingValue = profileLanguageLabel(profile.languageCode),
            onClick = { onEvent(MovitProfileEvent.LanguageClicked) },
            modifier = Modifier.semantics { contentDescription = languageRowA11y },
        )
        MovitListRow(
            title = movitText("profile_appearance"),
            subtitle = movitText("profile_appearance_sub"),
            trailingValue = profileAppearanceLabel(profile.themeMode),
            onClick = { onEvent(MovitProfileEvent.AppearanceClicked) },
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
                    modifier = Modifier.semantics { contentDescription = audioCuesA11y },
                )
            },
        )
        MovitListRow(
            title = movitText("profile_haptic"),
            showChevron = false,
            trailing = {
                Switch(
                    checked = profile.hapticEnabled,
                    onCheckedChange = { onEvent(MovitProfileEvent.HapticChanged(it)) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.semantics { contentDescription = hapticA11y },
                )
            },
        )
    }
    SettingsGroup(title = movitText("profile_account")) {
        MovitListRow(
            title = movitText("profile_edit_profile"),
            onClick = { onEvent(MovitProfileEvent.EditProfileClicked) },
        )
        MovitListRow(
            title = movitText("profile_training_profile"),
            subtitle = resolveTrainingProfileSummary(profile.trainingProfileSummary),
            onClick = { onEvent(MovitProfileEvent.TrainingProfileClicked) },
            modifier = Modifier.semantics { contentDescription = trainingProfileA11y },
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
        SignOutRow(onClick = { onEvent(MovitProfileEvent.LogoutClicked) })
    }
}

@Composable
private fun resolveTrainingProfileSummary(summary: String): String {
    return if (summary == TrainingProfileSummaryMapper.EMPTY_SUMMARY_KEY) {
        movitText(TrainingProfileSummaryMapper.EMPTY_SUMMARY_KEY)
    } else {
        summary
    }
}

@Composable
private fun SignOutRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MovitSpacing.lg, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = movitText("profile_sign_out"),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.W700,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun ProfileHero(
    profile: ProfileUi,
    onEvent: (MovitProfileEvent) -> Unit,
) {
    val avatarCd = movitText("profile_avatar_cd")
    val editCd = movitText("profile_edit_avatar_cd")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            val avatarFallback = profile.name
                .trim()
                .take(1)
                .uppercase()
                .ifBlank { "?" }
            MovitRemoteImage(
                imageUrl = profile.avatarUrl,
                contentDescription = avatarCd,
                placeholderLabel = avatarFallback,
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .semantics { contentDescription = avatarCd },
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable { onEvent(MovitProfileEvent.EditProfileClicked) }
                    .semantics { contentDescription = editCd },
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
            MovitTag(
                text = movitText("profile_pro_badge"),
                variant = MovitTagVariant.Gold,
            )
            Text(
                text = profile.subscriptionLabel,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.W800,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
            profile.subscriptionRenewal?.let { renewal ->
                Text(
                    text = renewal,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                    modifier = Modifier.padding(top = MovitSpacing.xs),
                )
            }
            if (PlatformInfo.supportsInAppSubscription) {
                MovitButton(
                    text = movitText("profile_manage_subscription"),
                    onClick = { onEvent(MovitProfileEvent.ManageSubscriptionClicked) },
                    variant = MovitButtonVariant.Outlined,
                    size = MovitButtonSize.Small,
                    modifier = Modifier.padding(top = MovitSpacing.md),
                )
            }
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
            if (PlatformInfo.supportsInAppSubscription) {
                MovitButton(
                    text = movitText("profile_view_plans"),
                    onClick = { onEvent(MovitProfileEvent.ViewPlansClicked) },
                    variant = MovitButtonVariant.Tonal,
                    size = MovitButtonSize.Small,
                    modifier = Modifier.padding(top = MovitSpacing.md),
                )
            } else {
                Text(
                    text = movitText("profile_subscription_ios_unavailable"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.movitColors.textSecondary,
                    modifier = Modifier.padding(top = MovitSpacing.md),
                )
            }
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
