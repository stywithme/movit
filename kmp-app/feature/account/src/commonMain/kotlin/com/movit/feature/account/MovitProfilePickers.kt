package com.movit.feature.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.movit.core.data.platform.MovitThemeModeStorage
import com.movit.designsystem.MovitSpacing
import com.movit.resources.movitText

@Composable
internal fun ProfileLanguagePickerDialog(
    selectedLanguageCode: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        "en" to movitText("profile_language_en"),
        "ar" to movitText("profile_language_ar"),
    )
    ProfileSingleChoiceDialog(
        title = movitText("profile_language"),
        options = options,
        selectedKey = selectedLanguageCode,
        onSelected = onLanguageSelected,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun ProfileAppearancePickerDialog(
    selectedThemeMode: String,
    onThemeModeSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        MovitThemeModeStorage.LIGHT to movitText("profile_appearance_light"),
        MovitThemeModeStorage.DARK to movitText("profile_appearance_dark"),
        MovitThemeModeStorage.SYSTEM to movitText("profile_appearance_system"),
    )
    ProfileSingleChoiceDialog(
        title = movitText("profile_appearance"),
        options = options,
        selectedKey = selectedThemeMode,
        onSelected = onThemeModeSelected,
        onDismiss = onDismiss,
    )
}

@Composable
internal fun ProfileLogoutConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = movitText("profile_logout_confirm_title")) },
        text = { Text(text = movitText("profile_logout_confirm_message")) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = movitText("profile_sign_out"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = movitText("profile_cancel"))
            }
        },
    )
}

@Composable
internal fun ProfileDeleteAccountConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = movitText("profile_delete_account_confirm_title")) },
        text = { Text(text = movitText("profile_delete_account_confirm_message")) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = movitText("profile_delete_account_confirm_action"),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = movitText("profile_cancel"))
            }
        },
    )
}

@Composable
private fun ProfileSingleChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                options.forEach { (key, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(key) }
                            .semantics { role = Role.RadioButton }
                            .padding(vertical = MovitSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedKey == key,
                            onClick = { onSelected(key) },
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = MovitSpacing.sm),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = movitText("profile_cancel"))
            }
        },
    )
}

@Composable
internal fun profileLanguageLabel(languageCode: String): String = when (languageCode.lowercase()) {
    "ar" -> movitText("profile_language_ar")
    else -> movitText("profile_language_en")
}

@Composable
internal fun profileAppearanceLabel(themeMode: String): String = when (themeMode.lowercase()) {
    MovitThemeModeStorage.LIGHT -> movitText("profile_appearance_light")
    MovitThemeModeStorage.DARK -> movitText("profile_appearance_dark")
    else -> movitText("profile_appearance_system")
}
