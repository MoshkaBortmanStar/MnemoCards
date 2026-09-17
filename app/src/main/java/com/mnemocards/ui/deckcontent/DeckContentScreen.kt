package com.mnemocards.ui.deckcontent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mnemocards.data.db.CardEntity

/**
 * Экран содержимого колоды (S1–S3, S9, S10, S15–S18, S24, S26).
 * Stateless по бизнес-данным: всё состояние приходит из [DeckContentUiState],
 * события текут вверх через коллбеки (UDF). Локально хранится только
 * состояние диалога подтверждения удаления (S15).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckContentScreen(
    state: DeckContentUiState,
    onBack: () -> Unit,
    onAddCard: () -> Unit,
    onEditCard: (Long) -> Unit,
    onDeleteCard: (Long) -> Unit,
    onRetry: () -> Unit,
) {
    // S15: карточка, ожидающая подтверждения удаления; до выбора остаётся в списке.
    var pendingDelete by remember { mutableStateOf<CardEntity?>(null) }

    Scaffold(
        topBar = {
            // Панель сливается с фоном: выделяться должны карточки, а не TopAppBar.
            TopAppBar(
                title = {
                    Text(
                        text = when (state) {
                            is DeckContentUiState.Content -> state.title
                            is DeckContentUiState.Empty -> state.title
                            DeckContentUiState.NotFound -> ""
                            else -> ""
                        },
                    )
                },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddCard,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить карточку",
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state) {
                DeckContentUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is DeckContentUiState.Content -> CardList(
                    cards = state.cards,
                    onEditCard = onEditCard,
                    onDelete = { pendingDelete = it },
                )

                is DeckContentUiState.Empty -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "В колоде пока нет карточек",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                }

                // S24: устаревший deckId — не выдаём пустой список за успех.
                DeckContentUiState.NotFound -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Колода не найдена",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Button(
                        onClick = onBack,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text("К списку колод")
                    }
                }

                // S26: ошибка чтения Room — повтор, а не пустой список.
                DeckContentUiState.Error -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Не удалось загрузить колоду",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text("Повторить")
                    }
                }
            }
        }
    }

    // S15–S17: диалог подтверждения удаления.
    pendingDelete?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить карточку?") },
            text = { Text("${card.front} — ${card.back}") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteCard(card.id)
                    },
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

/**
 * Список карточек: новые сверху (порядок задаёт DAO по createdAt DESC, S3).
 * Каждая запись показывает обе стороны (S1); дубликаты — независимые
 * элементы (S9); длинный текст переносится по строкам (S10).
 * Действие удаления открывает подтверждение (S15).
 */
@Composable
private fun CardList(
    cards: List<CardEntity>,
    onEditCard: (Long) -> Unit,
    onDelete: (CardEntity) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp,
            vertical = 8.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = cards, key = { it.id }) { card ->
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onEditCard(card.id) },
                    ) {
                        Text(
                            text = "${card.front} — ${card.back}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(card) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Удалить карточку",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
