package com.mnemocards.ui.decklist

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Диалог подтверждения удаления колоды (S12, S13): тап вне диалога
 * или «Отмена» закрывают диалог без изменений.
 */
@Composable
fun DeleteDeckDialog(
    deckName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить колоду?") },
        text = {
            Text("Колода «$deckName» и все её карточки будут удалены.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Удалить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
