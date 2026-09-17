package com.mnemocards.ui.decklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mnemocards.data.db.DeckWithCardCount

/**
 * Стартовый экран приложения — список колод (название + счётчик карточек).
 * Stateless по бизнес-данным: состояние приходит из [DeckListUiState],
 * события текут вверх через коллбеки (UDF). Тап по элементу колоды
 * открывает её содержимое (S1), кнопка «Тренировать» — тренировку (S22).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    state: DeckListUiState,
    onDeckClick: (Long) -> Unit,
    onTrainDeck: (Long) -> Unit,
) {
    Scaffold(
        topBar = {
            // Панель сливается с фоном — как на остальных экранах приложения.
            TopAppBar(
                title = { Text("Мои колоды") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (state) {
                DeckListUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

                is DeckListUiState.Content -> if (state.decks.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Колод пока нет",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = state.decks, key = { it.deckId }) { deck ->
                            DeckItem(
                                deck = deck,
                                onClick = { onDeckClick(deck.deckId) },
                                onTrain = { onTrainDeck(deck.deckId) },
                            )
                        }
                    }
                }

                // 500-аналог: ошибка чтения Room.
                DeckListUiState.Error -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Не удалось загрузить колоды",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

/**
 * Элемент колоды: тап по элементу — содержимое колоды (S1),
 * отдельная кнопка «Тренировать» — тренировка колоды (S22).
 */
@Composable
private fun DeckItem(
    deck: DeckWithCardCount,
    onClick: () -> Unit,
    onTrain: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = deck.deckName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${deck.cardCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onTrain) {
                Text("Тренировать")
            }
        }
    }
}
