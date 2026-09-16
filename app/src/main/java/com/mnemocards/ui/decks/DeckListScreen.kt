package com.mnemocards.ui.decks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mnemocards.domain.model.Deck

/**
 * Stateless-экран списка колод (T9, change `add-offline-storage`).
 *
 * Состояние и колбэки приходят параметрами (UDF); бизнес-логики в Compose нет.
 *
 * Сценарии spec-bdd:
 * - S3 — Content: LazyColumn, для каждой колоды название и счётчик карточек;
 * - S7 — Empty: заглушка «Пока нет колод»;
 * - S9 — Error: сообщение об ошибке и кнопка «Повторить»;
 * - S4 — тап по элементу вызывает [onDeckClick] с идентификатором колоды.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListScreen(
    uiState: DeckListUiState,
    onDeckClick: (deckId: Long) -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Колоды") }) },
    ) { padding ->
        when (uiState) {
            DeckListUiState.Loading -> CenteredColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                CircularProgressIndicator()
            }

            DeckListUiState.Empty -> CenteredColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Text(
                    text = "Пока нет колод",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            DeckListUiState.Error -> CenteredColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Text(
                    text = "Не удалось загрузить колоды",
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Повторить")
                }
            }

            is DeckListUiState.Content -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.decks, key = { it.id }) { deck ->
                    DeckListItem(deck = deck, onClick = { onDeckClick(deck.id) })
                }
            }
        }
    }
}

@Composable
private fun DeckListItem(
    deck: Deck,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = deck.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "${deck.cardCount} ${cardCountLabel(deck.cardCount)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Русская морфология счётчика: «1 карточка», «2 карточки», «5 карточек». */
private fun cardCountLabel(count: Int): String = when {
    count % 100 in 11..14 -> "карточек"
    count % 10 == 1 -> "карточка"
    count % 10 in 2..4 -> "карточки"
    else -> "карточек"
}

@Composable
private fun CenteredColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
    }
}
