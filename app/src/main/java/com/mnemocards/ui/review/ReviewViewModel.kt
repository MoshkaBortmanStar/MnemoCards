package com.mnemocards.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemocards.domain.repository.DeckRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/**
 * ViewModel сессии повторения `cards.review` (T11, change `add-offline-storage`).
 *
 * Захардкоженная колода удалена: карточки читаются из Room через
 * [DeckRepository] по идентификатору выбранной колоды ([deckId] приходит
 * из аргумента маршрута через [SavedStateHandle] и переживает пересоздание).
 *
 * MVVM + UDF: события текут вверх (публичные методы), состояние — вниз
 * ([uiState] через StateFlow); событие навигации «назад» — однонаправленное
 * ([navigateBack]).
 */
class ReviewViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: DeckRepository,
) : ViewModel() {

    /** Идентификатор выбранной колоды (аргумент маршрута `review/{deckId}`). */
    val deckId: Long = checkNotNull(savedStateHandle[DECK_ID_KEY])

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _navigateBack = Channel<Unit>(Channel.BUFFERED)
    val navigateBack: Flow<Unit> = _navigateBack.receiveAsFlow()

    private var cards: List<Pair<String, String>> = emptyList()
    private var index: Int = 0
    private var flipped: Boolean = false

    init {
        loadCards()
    }

    private fun loadCards() {
        viewModelScope.launch {
            val result = runCatching {
                repository.observeCards(deckId).first()
            }.flatten()

            val loaded = result.getOrDefault(emptyList())
            if (result.isFailure || loaded.isEmpty()) {
                // S8: устаревший элемент списка — колоды/карточек в БД нет.
                _uiState.value = ReviewUiState.NotFound
                _navigateBack.trySend(Unit)
            } else {
                cards = loaded.map { it.front to it.back }
                index = 0
                flipped = false
                publishSession()
            }
        }
    }

    /** Переворот карточки по тапу. */
    fun onFlip() {
        if (_uiState.value is ReviewUiState.Session && !flipped) {
            flipped = true
            publishSession()
        }
    }

    /**
     * Оценка ответа: четыре оценки эквивалентны — каждая завершает текущую
     * карточку и переходит к следующей либо к терминальному экрану.
     */
    fun onGrade(grade: ReviewGrade) {
        if (_uiState.value !is ReviewUiState.Session) return
        flipped = false
        if (index == cards.lastIndex) {
            _uiState.value = ReviewUiState.Finished
        } else {
            index += 1
            publishSession()
        }
    }

    /** Кнопка «назад»: запрос возврата к списку колод (S6). */
    fun onBack() {
        _navigateBack.trySend(Unit)
    }

    private fun publishSession() {
        val (front, back) = cards[index]
        _uiState.value = ReviewUiState.Session(
            front = front,
            back = back,
            flipped = flipped,
            position = index + 1,
            total = cards.size,
        )
    }

    private companion object {
        const val DECK_ID_KEY = "deckId"
    }
}

/** Разворачивает вложенный Result (Flow<Result<T>> → first) в один Result<T>. */
private fun <T> Result<Result<T>>.flatten(): Result<T> =
    fold({ it }, { Result.failure(it) })
