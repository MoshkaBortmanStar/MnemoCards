package com.mnemocards.ui.decklist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Диалог ввода названия колоды. Используется и для создания (S1–S5),
 * и для переименования (S8–S11) — при переименовании поле предзаполняется
 * текущим названием (S5: полный текст длинного названия доступен здесь).
 */
@Composable
fun DeckNameDialog(
    title: String,
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text("Название") },
                    isError = isError,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isError) {
                    Text(
                        text = "Название не может быть пустым",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}
