package com.movit.feature.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitDashboardHero
import com.movit.designsystem.components.MovitFilterChip
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.movitColors
import com.movit.resources.movitText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovitOnboardingScreen(
    state: MovitOnboardingUiState,
    onEvent: (MovitOnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(MovitSpacing.lg),
    ) {
        Text(
            text = movitText("onboarding_step_label", state.step + 1, OnboardingData.STEP_COUNT),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.movitColors.textTertiary,
        )
        MovitProgressBar(
            progressPercent = state.progressPercent,
            modifier = Modifier.padding(top = MovitSpacing.sm),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
        ) {
            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when (state.step) {
                OnboardingData.STEP_AGE_GENDER -> AgeGenderStep(state.data, onEvent)
                OnboardingData.STEP_BODY_METRICS -> BodyMetricsStep(state.data, onEvent)
                OnboardingData.STEP_EXPERIENCE -> ExperienceStep(state.data, onEvent)
                OnboardingData.STEP_GOAL -> GoalStep(state.data, onEvent)
                OnboardingData.STEP_WEEKDAYS -> WeekdaysStep(state.data, onEvent)
                OnboardingData.STEP_LOCATION -> LocationEquipmentStep(state.data, onEvent)
                OnboardingData.STEP_SUMMARY -> SummaryStep(state.data, onEvent)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md),
        ) {
            if (state.step > OnboardingData.STEP_AGE_GENDER) {
                MovitButton(
                    text = movitText("onboarding_back"),
                    onClick = { onEvent(MovitOnboardingEvent.BackClicked) },
                    variant = MovitButtonVariant.Outlined,
                    modifier = Modifier.weight(1f),
                )
            }
            MovitButton(
                text = if (state.step == OnboardingData.STEP_SUMMARY) {
                    movitText("onboarding_finish")
                } else {
                    movitText("onboarding_continue")
                },
                onClick = { onEvent(MovitOnboardingEvent.ContinueClicked) },
                enabled = state.canContinue,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AgeGenderStep(data: OnboardingData, onEvent: (MovitOnboardingEvent) -> Unit) {
    StepTitle(
        title = movitText("onboarding_about_you"),
        subtitle = movitText("onboarding_about_you_sub"),
    )
    MetricField(
        label = movitText("onboarding_age"),
        value = data.ageYears?.toString().orEmpty(),
        onValueChange = { onEvent(MovitOnboardingEvent.AgeChanged(it)) },
    )
    Text(
        text = movitText("onboarding_sex_label"),
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.padding(top = MovitSpacing.md),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        listOf("male", "female", "other").forEach { sex ->
            MovitFilterChip(
                label = movitText("onboarding_sex_$sex"),
                selected = data.biologicalSex == sex,
                onClick = { onEvent(MovitOnboardingEvent.SexSelected(sex)) },
            )
        }
    }
}

@Composable
private fun BodyMetricsStep(data: OnboardingData, onEvent: (MovitOnboardingEvent) -> Unit) {
    StepTitle(
        title = movitText("onboarding_metrics"),
        subtitle = movitText("onboarding_metrics_sub"),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.md)) {
        MetricField(
            label = movitText("onboarding_height"),
            value = data.heightCm?.toString().orEmpty(),
            onValueChange = { onEvent(MovitOnboardingEvent.HeightChanged(it)) },
            modifier = Modifier.weight(1f),
        )
        MetricField(
            label = movitText("onboarding_weight"),
            value = data.weightKg?.toString().orEmpty(),
            onValueChange = { onEvent(MovitOnboardingEvent.WeightChanged(it)) },
            modifier = Modifier.weight(1f),
        )
    }
    MovitInsightCard(
        title = movitText("onboarding_metrics_hint_title"),
        message = movitText("onboarding_metrics_hint_sub"),
        icon = Icons.Default.Check,
        variant = MovitInsightVariant.Success,
        modifier = Modifier.padding(top = MovitSpacing.md),
    )
}

@Composable
private fun ExperienceStep(data: OnboardingData, onEvent: (MovitOnboardingEvent) -> Unit) {
    MovitDashboardHero(
        eyebrow = movitText("onboarding_experience_eyebrow"),
        title = movitText("onboarding_experience_title"),
        subtitle = movitText("onboarding_experience_sub"),
        progressPercent = 0,
        inkStyle = false,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        listOf("beginner", "intermediate", "advanced").forEach { level ->
            MovitFilterChip(
                label = movitText("onboarding_exp_$level"),
                selected = data.resistanceExperience == level,
                onClick = { onEvent(MovitOnboardingEvent.ExperienceSelected(level)) },
            )
        }
    }
    Text(
        text = movitText("onboarding_sessions_per_week"),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(top = MovitSpacing.md),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        (1..7).forEach { days ->
            MovitFilterChip(
                label = days.toString(),
                selected = data.targetDaysPerWeek == days,
                onClick = { onEvent(MovitOnboardingEvent.TargetDaysChanged(days)) },
            )
        }
    }
}

@Composable
private fun GoalStep(data: OnboardingData, onEvent: (MovitOnboardingEvent) -> Unit) {
    StepTitle(
        title = movitText("onboarding_goal_title"),
        subtitle = movitText("onboarding_goal_sub"),
    )
    val goals = listOf(
        "STRENGTH" to movitText("onboarding_goal_strength"),
        "GENERAL_HEALTH" to movitText("onboarding_goal_weight"),
        "HYPERTROPHY" to movitText("onboarding_goal_mobility"),
    )
    goals.forEach { (code, label) ->
        val selected = data.trainingGoal == code
        MovitCard(
            variant = if (selected) MovitCardVariant.Filled else MovitCardVariant.Outlined,
            onClick = { onEvent(MovitOnboardingEvent.GoalSelected(code)) },
        ) {
            Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.W700)
            Text(
                text = movitText("onboarding_goal_${code.lowercase()}_sub"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.movitColors.textSecondary,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
        }
    }
}

@Composable
private fun WeekdaysStep(data: OnboardingData, onEvent: (MovitOnboardingEvent) -> Unit) {
    StepTitle(
        title = movitText("onboarding_days_title"),
        subtitle = movitText("onboarding_days_sub"),
    )
    val labels = listOf("M", "T", "W", "T", "F", "S", "S")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        labels.forEachIndexed { index, label ->
            MovitFilterChip(
                label = label,
                selected = data.trainingWeekdays.contains(index),
                onClick = { onEvent(MovitOnboardingEvent.WeekdayToggled(index)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocationEquipmentStep(data: OnboardingData, onEvent: (MovitOnboardingEvent) -> Unit) {
    StepTitle(
        title = movitText("onboarding_location_title"),
        subtitle = movitText("onboarding_location_sub"),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        listOf("home", "gym").forEach { location ->
            MovitFilterChip(
                label = movitText("onboarding_location_$location"),
                selected = data.trainingLocation == location,
                onClick = { onEvent(MovitOnboardingEvent.LocationSelected(location)) },
            )
        }
    }
    if (data.trainingLocation == "home") {
        Text(
            text = movitText("onboarding_equipment_title"),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = MovitSpacing.md),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
            OnboardingData.HOME_EQUIPMENT.forEach { code ->
                MovitFilterChip(
                    label = movitText("onboarding_equipment_$code"),
                    selected = data.availableEquipment.contains(code),
                    onClick = {
                        val enabled = !data.availableEquipment.contains(code)
                        onEvent(MovitOnboardingEvent.EquipmentToggled(code, enabled))
                    },
                )
            }
        }
    }
}

@Composable
private fun SummaryStep(data: OnboardingData, onEvent: (MovitOnboardingEvent) -> Unit) {
    StepTitle(
        title = movitText("onboarding_summary_title"),
        subtitle = movitText("onboarding_summary_sub"),
    )
    MovitCard {
        SummaryRow(movitText("onboarding_summary_age"), "${data.ageYears ?: "—"} · ${data.biologicalSex ?: "—"}")
        SummaryRow(
            movitText("onboarding_summary_metrics"),
            "${data.heightCm ?: "—"} cm · ${data.weightKg ?: "—"} kg",
        )
        SummaryRow(movitText("onboarding_summary_experience"), data.resistanceExperience ?: "—")
        SummaryRow(movitText("onboarding_summary_goal"), data.trainingGoal ?: "—")
        SummaryRow(movitText("onboarding_summary_days"), data.trainingWeekdays.sorted().joinToString())
        SummaryRow(movitText("onboarding_summary_equipment"), data.trainingLocation ?: "—")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = MovitSpacing.md),
    ) {
        Checkbox(
            checked = data.healthDisclaimerAccepted,
            onCheckedChange = { onEvent(MovitOnboardingEvent.DisclaimerChanged(it)) },
        )
        Text(
            text = movitText("onboarding_disclaimer"),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MovitSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, color = MaterialTheme.movitColors.textSecondary)
        Text(text = value, fontWeight = FontWeight.W700)
    }
}

@Composable
private fun StepTitle(title: String, subtitle: String) {
    Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.W800)
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.movitColors.textSecondary,
        modifier = Modifier.padding(top = MovitSpacing.xs),
    )
}

@Composable
private fun MetricField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.labelMedium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MovitSpacing.xs),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}
