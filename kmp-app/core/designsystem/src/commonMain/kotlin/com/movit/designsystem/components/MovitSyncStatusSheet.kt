package com.movit.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class MovitSyncStatusSheetModel(
    val title: String,
    val message: String,
    val pendingOutbox: Long = 0,
    val failedOutbox: Long = 0,
    val syncNowLabel: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovitSyncStatusSheet(
    model: MovitSyncStatusSheetModel,
    onDismiss: () -> Unit,
    onSyncNow: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Text(text = model.title, style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = model.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (model.pendingOutbox > 0 || model.failedOutbox > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = buildString {
                        if (model.pendingOutbox > 0) append("Pending: ${model.pendingOutbox}")
                        if (model.failedOutbox > 0) {
                            if (isNotEmpty()) append(" · ")
                            append("Failed: ${model.failedOutbox}")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    onSyncNow()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(model.syncNowLabel)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
