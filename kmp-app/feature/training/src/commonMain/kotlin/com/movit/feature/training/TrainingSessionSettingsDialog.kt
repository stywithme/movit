package com.movit.feature.training

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.movit.core.data.preferences.MovitTrainingPreferencesState
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

data class TrainingSessionSettingsSelection(
    val indicatorType: String,
    val voiceFeedbackEnabled: Boolean,
    val coachIntensity: String,
    val modelType: String,
)

@Composable
internal fun TrainingSessionSettingsDialog(
    preferences: MovitTrainingPreferencesState,
    useFrontCamera: Boolean,
    onSwitchCamera: (() -> Unit)?,
    onApply: (TrainingSessionSettingsSelection) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedIndicator by remember(preferences.indicatorType) {
        mutableStateOf(preferences.indicatorType)
    }
    var voiceEnabled by remember(preferences.voiceFeedbackEnabled) {
        mutableStateOf(preferences.voiceFeedbackEnabled)
    }
    var selectedCoach by remember(preferences.coachIntensity) {
        mutableStateOf(preferences.coachIntensity)
    }
    var selectedModel by remember(preferences.modelType) {
        mutableStateOf(preferences.modelType)
    }

    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = movitText("training_settings"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.W800,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MovitSpacing.md),
            ) {
                SettingsSectionTitle(text = movitText("visual_indicator"))
                SettingsChoiceGroup(
                    selected = selectedIndicator,
                    options = listOf(
                        SettingsOption("line", movitText("indicator_line")),
                        SettingsOption("arc", movitText("indicator_arc")),
                    ),
                    onSelected = { selectedIndicator = it },
                )

                SettingsDivider()

                SettingsSwitchRow(
                    title = movitText("voice_feedback"),
                    subtitle = movitText("voice_feedback_desc"),
                    checked = voiceEnabled,
                    onCheckedChange = { voiceEnabled = it },
                )

                SettingsDivider()

                SettingsSectionTitle(text = movitText("coach_intensity"))
                SettingsDescription(text = movitText("coach_intensity_desc"))
                SettingsChoiceGroup(
                    selected = selectedCoach,
                    options = listOf(
                        SettingsOption("calm", movitText("coach_calm")),
                        SettingsOption("standard", movitText("coach_standard")),
                        SettingsOption("strict", movitText("coach_strict")),
                    ),
                    onSelected = { selectedCoach = it },
                )

                SettingsDivider()

                SettingsSectionTitle(text = movitText("detection_model"))
                SettingsDescription(text = movitText("detection_model_desc"))
                SettingsChoiceGroup(
                    selected = selectedModel,
                    options = listOf(
                        SettingsOption("full", movitText("model_full")),
                        SettingsOption("heavy", movitText("model_heavy")),
                    ),
                    onSelected = { selectedModel = it },
                )

                if (onSwitchCamera != null) {
                    SettingsDivider()
                    CameraPositionRow(
                        useFrontCamera = useFrontCamera,
                        onSwitchCamera = onSwitchCamera,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        TrainingSessionSettingsSelection(
                            indicatorType = selectedIndicator,
                            voiceFeedbackEnabled = voiceEnabled,
                            coachIntensity = selectedCoach,
                            modelType = selectedModel,
                        ),
                    )
                },
            ) {
                Text(movitText("apply"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(movitText("common_cancel"))
            }
        },
    )
}

private data class SettingsOption(
    val value: String,
    val label: String,
)

@Composable
private fun SettingsSectionTitle(text: String) {
    Text(
        text = text,
        color = MaterialTheme.movitColors.textSecondary,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.W800,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun SettingsDescription(text: String) {
    Text(
        text = text,
        color = MaterialTheme.movitColors.textTertiary,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun SettingsChoiceGroup(
    selected: String,
    options: List<SettingsOption>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        options.forEach { option ->
            SettingsChoiceButton(
                label = option.label,
                selected = selected.equals(option.value, ignoreCase = true),
                onClick = { onSelected(option.value) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SettingsChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colors.primary else colors.surfaceVariant,
        contentColor = if (selected) colors.onPrimary else colors.onSurface,
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = MovitSpacing.sm, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.W800,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W800,
            )
            Text(
                text = subtitle,
                color = MaterialTheme.movitColors.textTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun CameraPositionRow(
    useFrontCamera: Boolean,
    onSwitchCamera: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = movitText("camera_position"),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.W800,
            )
            Text(
                text = if (useFrontCamera) {
                    movitText("front_camera")
                } else {
                    movitText("back_camera")
                },
                color = MaterialTheme.movitColors.textTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        SettingsChoiceButton(
            label = movitText("switch_text"),
            selected = false,
            onClick = onSwitchCamera,
        )
    }
}

@Composable
private fun SettingsDivider() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}
