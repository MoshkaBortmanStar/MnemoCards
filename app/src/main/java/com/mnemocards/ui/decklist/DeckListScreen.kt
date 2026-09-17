package com.mnemocards.ui.decklist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Экран «Список колод». UDF: события текут вверх в ViewModel,
 * состояние приходит вниз из StateFlow. Список читается из Room
 * через Flow репозитория, поэтому после мутаций обновляется сам.
 * Рядом с общим количеством карточек — счётчик готовых к повторению
 * сегодня (S25, S27), после оценок он обновляется автоматически (S26).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    viewModel: DeckListViewModel,
    onDeckClick: (DeckListItem) -> Unit,
    onTrainDeck: (Long) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            // Панель сливается с фоном — как на остальных экранах приложения.
            TopAppBar(
                title = { Text("Колоды") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::openCreateDialog,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить колоду"
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Ошибка репозитория (аналог 500): экран остаётся консистентным
            // с фактическим состоянием Room (S22)
            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            if (uiState.decks.isEmpty()) {
                // Пустое состояние (S16, S17): приглашение создать первую колоду
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Пока нет колод",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = viewModel::openCreateDialog) {
                        Text("Создать первую колоду", maxLines = 1, softWrap = false)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.decks, key = { it.id }) { deck ->
                        DeckItem(
                            deck = deck,
                            isMenuShown = uiState.isMenuShown &&
                                uiState.selectedDeck?.id == deck.id,
                            onClick = { onDeckClick(deck) },
                            onLongClick = { viewModel.onDeckLongPressed(deck) },
                            onRename = viewModel::openRenameDialog,
                            onDelete = viewModel::openDeleteDialog,
                            onDismissMenu = viewModel::dismissMenu,
                            onTrain = { onTrainDeck(deck.id) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.isCreateDialogShown) {
        DeckNameDialog(
            title = "Новая колода",
            value = uiState.createDeckName,
            isError = uiState.isCreateNameError,
            onValueChange = viewModel::updateCreateDeckName,
            onSave = viewModel::createDeck,
            onDismiss = viewModel::dismissCreateDialog
        )
    }

    if (uiState.isRenameDialogShown) {
        DeckNameDialog(
            title = "Переименовать колоду",
            value = uiState.renameDeckName,
            isError = uiState.isRenameNameError,
            onValueChange = viewModel::updateRenameDeckName,
            onSave = viewModel::renameDeck,
            onDismiss = viewModel::dismissRenameDialog
        )
    }

    if (uiState.isDeleteDialogShown) {
        DeleteDeckDialog(
            deckName = uiState.selectedDeck?.name.orEmpty(),
            onConfirm = viewModel::deleteDeck,
            onDismiss = viewModel::dismissDeleteDialog
        )
    }
}

/**
 * Пункт списка колод: тап — открыть содержимое колоды, долгое
 * нажатие (S6) — контекстное меню ровно с двумя действиями.
 * Название обрезается одной строкой с ellipsis, в Room хранится
 * полный текст (S5). Под названием — общее количество карточек
 * и сколько готово к повторению сегодня (S25, S27).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeckItem(
    deck: DeckListItem,
    isMenuShown: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onDismissMenu: () -> Unit,
    onTrain: () -> Unit
) {
    Box {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = deck.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${deck.total} карточек · готово сегодня: ${deck.dueToday}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            TextButton(
                onClick = onTrain,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text("Тренировать", maxLines = 1, softWrap = false)
            }
        }
        DropdownMenu(
            expanded = isMenuShown,
            onDismissRequest = onDismissMenu
        ) {
            DropdownMenuItem(
                text = { Text("Переименовать") },
                onClick = onRename
            )
            DropdownMenuItem(
                text = { Text("Удалить") },
                onClick = onDelete
            )
        }
    }
}
