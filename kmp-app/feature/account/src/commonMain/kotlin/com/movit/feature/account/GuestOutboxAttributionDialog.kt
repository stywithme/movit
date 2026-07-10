package com.movit.feature.account

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.movit.resources.movitText

/** UX.7 — attribute or discard guest workouts after login/register. */
@Composable
fun GuestOutboxAttributionDialog(
    guestRowCount: Int,
    onAccept: () -> Unit,
    onDiscard: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { /* require explicit choice — shared device safety */ },
        title = { Text(text = movitText("auth_guest_outbox_title")) },
        text = {
            Text(
                text = movitText("auth_guest_outbox_message")
                    .replace("%1\$d", guestRowCount.toString()),
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(text = movitText("auth_guest_outbox_accept"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text(text = movitText("auth_guest_outbox_discard"))
            }
        },
    )
}
