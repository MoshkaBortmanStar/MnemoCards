package com.mnemocards.ui.decklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckWithCardCount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Состояние экрана списка колод. Error — 500-аналог при чтении Room.
 */
sealed interface DeckListUiState {
    data object Loading : DeckListUiState
    data class Content(val decks: List<DeckWithCardCount>) : DeckListUiState
    data object Error : DeckListUiState
}

/**
 * ViewModel списка колод (MVVM + UDF).
 * Подписана на реактивный Flow из Room: счётчики карточек колод
 * обновляются автоматически после любых правок карточек (S20, S21),
 * без пересоздания экрана.
 */
class DeckListViewModel(
    private val deckDao: DeckDao,
) : ViewModel() {

    val uiState: StateFlow<DeckListUiState> = deckDao.observeDecksWithCardCounts()
        // Явный тип R: иначе map зафиксирует Content, и catch не сможет
        // эмитить Error (общий супертип — DeckListUiState).
        .map<List<DeckWithCardCount>, DeckListUiState> { DeckListUiState.Content(it) }
        .catch {
            // 500-аналог: ошибка чтения Room не выдаётся за пустой список.
            emit(DeckListUiState.Error)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeckListUiState.Loading,
        )
}
