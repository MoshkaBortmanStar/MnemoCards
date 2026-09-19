package com.mnemocards.ui.syncsettings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mnemocards.MnemoCardsApplication
import com.mnemocards.data.db.sync.LastSyncResultEntity
import com.mnemocards.data.sync.SyncStatus
import java.text.DateFormat
import java.util.Date

/**
 * Экран настроек синхронизации (S1): поля «Адрес сервера», «Логин»,
 * «Пароль», кнопки «Проверить соединение» и «Войти», индикатор статуса
 * «Не подключено»/«Подключено», кнопка «Выйти» в подключённом состоянии.
 * Бизнес-логики нет — только отображение состояния из ViewModel.
 *
 * ViewModel подключается маршрутом в AppNavHost (UDF); состояние
 * приходит вниз через collectAsStateWithLifecycle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    state: SyncSettingsUiState,
    onAddressChanged: (String) -> Unit,
    onUsernameChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onCheckConnection: () -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onSyncNow: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Синхронизация") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                // Панель сливается с фоном — выделяться должен контент.
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Индикатор состояния подключения (S1, S18).
            Text(
                text = if (state.isConnected) {
                    "Подключено: ${state.address}"
                } else {
                    "Не подключено"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (state.isConnected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )

            OutlinedTextField(
                value = state.address,
                onValueChange = onAddressChanged,
                label = { Text("Адрес сервера") },
                isError = state.addressError != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    state.addressError?.let { Text(it) }
                },
            )

            OutlinedTextField(
                value = state.username,
                onValueChange = onUsernameChanged,
                label = { Text("Логин") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = onPasswordChanged,
                label = { Text("Пароль") },
                isError = state.credentialsError != null,
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                supportingText = {
                    state.credentialsError?.let { Text(it) }
                },
            )

            if (state.isConnected) {
                // change-009: статус и итог фоновой синхронизации —
                // только здесь, не на экранах колод (S21, S2, S13).
                SyncStatusSection(
                    status = state.syncStatus,
                    lastSync = state.lastSync,
                    onSyncNow = onSyncNow,
                )
                OutlinedButton(
                    onClick = onLogout,
                    enabled = !state.isChecking && !state.isLoggingIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Выйти")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onCheckConnection,
                        enabled = !state.isChecking && !state.isLoggingIn,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Проверить соединение")
                    }
                    Button(
                        onClick = onLogin,
                        enabled = !state.isChecking && !state.isLoggingIn,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (state.isLoggingIn) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Войти")
                    }
                }
            }

            // Текстовый результат проверки/входа (S5–S9, S11–S14).
            state.resultText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(8.dp))

            // Дублирующая точка выравнивания контента при скролле.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {}
        }
    }
}

/**
 * Секция фоновой синхронизации (change-009): кнопка «Синхронизировать
 * сейчас», статус текущей сессии, время и ошибка последней (S2, S13).
 * Repeat-нажатие при выполнении не создаёт вторую сессию (S23).
 */
@Composable
private fun SyncStatusSection(
    status: SyncStatus,
    lastSync: LastSyncResultEntity?,
    onSyncNow: () -> Unit,
) {
    Text(
        text = "Синхронизация",
        style = MaterialTheme.typography.titleMedium,
    )
    Button(
        onClick = onSyncNow,
        enabled = status !is SyncStatus.Running,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (status is SyncStatus.Running) {
            CircularProgressIndicator(
                modifier = Modifier
                    .width(20.dp)
                    .height(20.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text("Синхронизировать сейчас")
    }
    val statusText = when (status) {
        SyncStatus.Running -> "Выполняется"
        SyncStatus.Success -> "Успешно"
        SyncStatus.RequiresLogin -> "Требуется вход"
        is SyncStatus.Error -> "Ошибка: ${status.error.message}"
        SyncStatus.Idle -> lastSync?.let { "Завершена ранее" } ?: "Ещё не выполнялась"
    }
    Text(
        text = statusText,
        style = MaterialTheme.typography.bodyMedium,
        color = if (status is SyncStatus.Error || status is SyncStatus.RequiresLogin) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
    // Итог последней сессии: время и текст ошибки (S2, S15).
    lastSync?.let { last ->
        val time = DateFormat.getDateTimeInstance().format(Date(last.finishedAtMillis))
        Text(
            text = buildString {
                append("Последняя синхронизация: ")
                append(
                    when (last.status) {
                        LastSyncResultEntity.STATUS_SUCCESS -> "успешна"
                        LastSyncResultEntity.STATUS_REQUIRES_LOGIN -> "требуется вход"
                        else -> "с ошибкой"
                    },
                )
                append(" — ").append(time)
                last.errorMessage?.let { append("\n").append(it) }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
