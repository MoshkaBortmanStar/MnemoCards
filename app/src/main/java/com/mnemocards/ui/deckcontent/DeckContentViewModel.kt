package com.mnemocards.ui.deckcontent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Состояние экрана содержимого колоды.
 * NotFound — 404-аналог (S24), Error — 500-аналог при чтении (S26).
 */
sealed interface DeckContentUiState {
    data object Loading : DeckContentUiState
    data class Content(val title: String, val cards: List<CardEntity>) : DeckContentUiState
    data class Empty(val title: String) : DeckContentUiState
    data object NotFound : DeckContentUiState
    data object Error : DeckContentUiState
}

/**
 * ViewModel экрана содержимого колоды (MVVM + UDF).
 * Объединяет потоки колоды и её карточек из Room; состояние
 * живёт в StateFlow и переживает смену конфигурации.
 */
class DeckContentViewModel(
    private val deckId: Long,
    private val repository: CardRepository,
) : ViewModel() {

    /** Счётчик попыток загрузки: инкремент в retry() перезапускает потоки. */
    private val retryTrigger = MutableStateFlow(0)

    val uiState: StateFlow<DeckContentUiState> = retryTrigger
        .flatMapLatest { attempt ->
            combine(
                repository.observeDeck(deckId),
                repository.observeCards(deckId),
            ) { deck, cards ->
                when {
                    // S24: устаревший идентификатор колоды — не выдаём пустой список за успех.
                    deck == null -> DeckContentUiState.NotFound
                    cards.isEmpty() -> DeckContentUiState.Empty(deck.name)
                    else -> DeckContentUiState.Content(deck.name, cards)
                }
            }.catch {
                // S26: ошибка чтения Room — состояние ошибки, пустой список не выдаётся за успех.
                emit(DeckContentUiState.Error)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DeckContentUiState.Loading,
        )

    /** Повторная загрузка после ошибки чтения (S26). */
    fun retry() {
        retryTrigger.value = retryTrigger.value + 1
    }

    /** Удаление карточки; делегируется в репозиторий, ошибки не роняют UI (S16, S26). */
    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            repository.deleteCard(cardId)
        }
    }
}
