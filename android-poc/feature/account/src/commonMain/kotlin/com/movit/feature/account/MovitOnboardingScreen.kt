package com.movit.feature.account

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.movit.designsystem.MovitRadius
import com.movit.designsystem.MovitSpacing
import com.movit.designsystem.components.MovitButton
import com.movit.designsystem.components.MovitButtonVariant
import com.movit.designsystem.components.MovitCard
import com.movit.designsystem.components.MovitCardVariant
import com.movit.designsystem.components.MovitDashboardHero
import com.movit.designsystem.components.MovitDashboardHeroVariant
import com.movit.designsystem.components.MovitIconBox
import com.movit.designsystem.components.MovitIconBoxVariant
import com.movit.designsystem.components.MovitInsightCard
import com.movit.designsystem.components.MovitInsightVariant
import com.movit.designsystem.components.MovitBanner
import com.movit.designsystem.components.MovitProgressBar
import com.movit.designsystem.movitColors
import com.movit.resources.movitText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovitOnboardingScreen(
    state: MovitOnboardingUiState,
    onEvent: (MovitOnboardingEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressA11y = movitText(
        "onboarding_progress_a11y",
        state.step + 1,
        OnboardingData.STEP_COUNT,
    )

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
            modifier = Modifier
                .padding(top = MovitSpacing.sm)
                .semantics { contentDescription = progressA11y },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = MovitSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.lg),
        ) {
            if (state.submitErrorMessage != null) {
                Column(verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
                    MovitBanner(
                        title = movitText("onboarding_save_failed_title"),
                        message = state.submitErrorMessage,
                    )
                    MovitButton(
                        text = movitText("onboarding_retry_save"),
                        onClick = { onEvent(MovitOnboardingEvent.RetrySubmitClicked) },
                        variant = MovitButtonVariant.Outlined,
                        enabled = !state.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (state.validationErrorKey != null) {
                Text(
                    text = movitText(state.validationErrorKey),
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
                    enabled = !state.isSubmitting,
                    modifier = Modifier.weight(1f),
                )
            }
            MovitButton(
                text = when {
                    state.isSubmitting -> movitText("onboarding_saving")
                    state.step == OnboardingData.STEP_SUMMARY -> movitText("onboarding_finish")
                    else -> movitText("onboarding_continue")
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
    OnboardingIllustration(
        icon = Icons.Default.Person,
        contentDescription = movitText("onboarding_about_you"),
    )
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = MovitSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        listOf("male", "female", "other").forEach { sex ->
            val label = movitText("onboarding_sex_$sex")
            GenderCard(
                label = label,
                selected = data.biologicalSex == sex,
                onClick = { onEvent(MovitOnboardingEvent.SexSelected(sex)) },
                modifier = Modifier.weight(1f),
                contentDescription = label,
            )
        }
    }
}

@Composable
private fun BodyMetricsStep(data: OnboardingData, onEvent: (MovitOnboardingEvent) -> Unit) {
    OnboardingIllustration(
        icon = Icons.Default.FitnessCenter,
        contentDescription = movitText("onboarding_metrics"),
    )
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
        variant = MovitDashboardHeroVariant.Default,
        showProgress = false,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm)) {
        listOf("beginner", "intermediate", "advanced").forEach { level ->
            MovitSelectionChip(
                label = movitText("onboarding_exp_$level"),
                selected = data.resistanceExperience == level,
                onClick = { onEvent(MovitOnboardingEvent.ExperienceSelected(level)) },
            )
        }
    }
    Column(modifier = Modifier.padding(top = MovitSpacing.lg)) {
        Text(
            text = movitText("onboarding_sessions_per_week"),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = movitText("onboarding_sessions_value", data.targetDaysPerWeek ?: OnboardingData.DEFAULT_DAYS_PER_WEEK),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.W700,
            modifier = Modifier.padding(top = MovitSpacing.xs),
        )
        Slider(
            value = (data.targetDaysPerWeek ?: OnboardingData.DEFAULT_DAYS_PER_WEEK).toFloat(),
            onValueChange = { onEvent(MovitOnboardingEvent.TargetDaysChanged(it.toInt())) },
            valueRange = 1f..7f,
            steps = 5,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GoalStep(data: OnboardingData, onEvent: (MovitOnboardingEvent) -> Unit) {
    OnboardingIllustration(
        icon = Icons.Default.TrackChanges,
        contentDescription = movitText("onboarding_goal_title"),
    )
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        OnboardingData.WEEKDAY_DISPLAY_ORDER.forEachIndexed { displayIndex, dayIndex ->
            val label = movitText(OnboardingData.WEEKDAY_LABEL_KEYS[dayIndex])
            val shortLabel = OnboardingData.WEEKDAY_SHORT_LETTERS[displayIndex]
            WeekdayCell(
                label = shortLabel,
                selected = data.trainingWeekdays.contains(dayIndex),
                onClick = { onEvent(MovitOnboardingEvent.WeekdayToggled(dayIndex)) },
                modifier = Modifier.weight(1f),
                contentDescription = label,
            )
        }
    }
    val target = data.targetDaysPerWeek ?: 0
    if (target > 0) {
        Text(
            text = movitText("onboarding_weekdays_hint", data.trainingWeekdays.size, target),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.movitColors.textSecondary,
            modifier = Modifier.padding(top = MovitSpacing.sm),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LocationEquipmentStep(data: OnboardingData, onEvent: (MovitOnboardingEvent) -> Unit) {
    StepTitle(
        title = movitText("onboarding_location_title"),
        subtitle = movitText("onboarding_location_sub"),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
    ) {
        listOf("home", "gym").forEach { location ->
            val label = movitText("onboarding_location_$location")
            val subtitle = movitText("onboarding_location_${location}_sub")
            LocationCard(
                title = label,
                subtitle = subtitle,
                selected = data.trainingLocation == location,
                onClick = { onEvent(MovitOnboardingEvent.LocationSelected(location)) },
                modifier = Modifier.weight(1f),
                contentDescription = label,
            )
        }
    }
    if (data.trainingLocation == "home") {
        Text(
            text = movitText("onboarding_equipment_title"),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = MovitSpacing.lg),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(MovitSpacing.sm),
            modifier = Modifier.padding(top = MovitSpacing.sm),
        ) {
            OnboardingData.HOME_EQUIPMENT.forEach { code ->
                val label = movitText("onboarding_equipment_$code")
                EquipmentCard(
                    title = label,
                    selected = data.availableEquipment.contains(code),
                    onClick = {
                        val enabled = !data.availableEquipment.contains(code)
                        onEvent(MovitOnboardingEvent.EquipmentToggled(code, enabled))
                    },
                    modifier = Modifier.fillMaxWidth(0.48f),
                    contentDescription = label,
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
        Text(
            text = movitText("onboarding_summary_profile_label"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.movitColors.textTertiary,
            modifier = Modifier.padding(bottom = MovitSpacing.sm),
        )
        SummaryRow(
            movitText("onboarding_summary_age"),
            formatAgeSexSummary(data),
        )
        SummaryRow(
            movitText("onboarding_summary_metrics"),
            formatMetricsSummary(data),
        )
        SummaryRow(
            movitText("onboarding_summary_experience"),
            data.resistanceExperience?.let { movitText("onboarding_exp_$it") } ?: "—",
        )
        SummaryRow(
            movitText("onboarding_summary_goal"),
            formatGoalSummary(data),
        )
        SummaryRow(
            movitText("onboarding_summary_days"),
            formatWeekdaysSummary(data),
        )
        SummaryRow(
            movitText("onboarding_summary_equipment"),
            formatLocationEquipmentSummary(data),
        )
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
private fun OnboardingIllustration(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    MovitIconBox(
        icon = icon,
        variant = MovitIconBoxVariant.Primary,
        contentDescription = contentDescription,
        modifier = Modifier.padding(bottom = MovitSpacing.sm),
    )
}

@Composable
private fun GenderCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.movitColors.primaryTint else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.W700,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 18.dp, horizontal = 8.dp),
        )
    }
}

@Composable
private fun WeekdayCell(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .aspectRatio(1f)
            .semantics { this.contentDescription = contentDescription },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(MovitRadius.lg),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.W700,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun LocationCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.movitColors.primaryTint else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Column(modifier = Modifier.padding(MovitSpacing.md)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.W700)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.movitColors.textSecondary,
                modifier = Modifier.padding(top = MovitSpacing.xs),
            )
        }
    }
}

@Composable
private fun EquipmentCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.contentDescription = contentDescription },
        shape = MaterialTheme.shapes.large,
        color = if (selected) MaterialTheme.movitColors.limeTint else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (selected) MaterialTheme.movitColors.limeDeep else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.W700,
            modifier = Modifier.padding(MovitSpacing.md),
        )
    }
}

@Composable
private fun MovitSelectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.movitColors.primaryTint else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.5.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.W700,
            modifier = Modifier.padding(horizontal = MovitSpacing.md, vertical = MovitSpacing.sm),
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
        Text(text = value, fontWeight = FontWeight.W700, textAlign = TextAlign.End)
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

@Composable
private fun formatAgeSexSummary(data: OnboardingData): String {
    val age = data.ageYears?.toString() ?: "—"
    val sex = data.biologicalSex?.let { movitText("onboarding_sex_$it") } ?: "—"
    return "$age · $sex"
}

@Composable
private fun formatMetricsSummary(data: OnboardingData): String {
    val height = data.heightCm?.let { "${it.toInt()} cm" } ?: "—"
    val weight = data.weightKg?.let { "${it.toInt()} kg" } ?: "—"
    return "$height · $weight"
}

@Composable
private fun formatGoalSummary(data: OnboardingData): String = when (data.trainingGoal) {
    "STRENGTH" -> movitText("onboarding_goal_strength")
    "GENERAL_HEALTH" -> movitText("onboarding_goal_weight")
    "HYPERTROPHY" -> movitText("onboarding_goal_mobility")
    else -> "—"
}

@Composable
private fun formatWeekdaysSummary(data: OnboardingData): String {
    if (data.trainingWeekdays.isEmpty()) return "—"
    return data.trainingWeekdays.sorted().map { index ->
        movitText(OnboardingData.WEEKDAY_LABEL_KEYS.getOrElse(index) { "onboarding_weekday_sun" })
    }.joinToString(", ")
}

@Composable
private fun formatLocationEquipmentSummary(data: OnboardingData): String {
    val location = data.trainingLocation?.let { movitText("onboarding_location_$it") } ?: return "—"
    if (data.trainingLocation == "gym") {
        return "$location · ${movitText("onboarding_equipment_all")}"
    }
    val equipmentLabels = data.availableEquipment
        .plus("bodyweight")
        .distinct()
        .map { code ->
            if (code == "bodyweight") {
                movitText("onboarding_equipment_bodyweight")
            } else {
                movitText("onboarding_equipment_$code")
            }
        }
    return "$location · ${equipmentLabels.joinToString(", ")}"
}
