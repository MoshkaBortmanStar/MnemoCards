package com.mnemocards.ui.cardform

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Форма карточки: добавление (S4–S10) и редактирование (S11–S14).
 * Stateless по бизнес-данным: состояние приходит из [CardFormUiState],
 * события текут вверх через коллбеки (UDF). Кнопка «Сохранить» всегда
 * активна, ошибки валидации показываются по нажатию (S6–S8, S13).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardFormScreen(
    state: CardFormUiState,
    onFrontChange: (String) -> Unit,
    onBackChange: (String) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    // S14: системный «Назад» закрывает форму без сохранения.
    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            // Панель сливается с фоном — как на остальных экранах приложения.
            TopAppBar(
                title = { Text("Карточка") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.front,
                onValueChange = onFrontChange,
                label = { Text("Лицевая сторона") },
                isError = state.frontError != null,
                supportingText = {
                    if (state.frontError != null) {
                        Text(state.frontError)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.back,
                onValueChange = onBackChange,
                label = { Text("Перевод") },
                isError = state.backError != null,
                supportingText = {
                    if (state.backError != null) {
                        Text(state.backError)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = onSave,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Сохранить")
            }

            // S26: 500-аналог — сообщение при ошибке записи, ввод не теряется.
            if (state.saveError != null) {
                Text(
                    text = state.saveError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
