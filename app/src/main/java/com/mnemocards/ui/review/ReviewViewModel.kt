package com.mnemocards.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mnemocards.data.repository.CardRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Состояние сессии повторения. isFinished — терминальный экран
 * «на сегодня всё». Текущая карточка идентифицируется по id, поэтому
 * эмиссия Flow с изменённым текстом обновляет экран без пересоздания
 * сессии (S23).
 */
data class ReviewUiState(
    val front: String? = null,
    val back: String? = null,
    val isRevealed: Boolean = false,
    val isFinished: Boolean = false,
)

/**
 * ViewModel сессии повторения (MVVM + UDF).
 * Карточки наблюдаются через Flow из Room (S22, S23): новые карточки
 * входят в сессию, правки текста отражаются в открытой сессии.
 */
class ReviewViewModel(
    deckId: Long,
    private val repository: CardRepository,
) : ViewModel() {

    private val isRevealed = MutableStateFlow(false)
    private val isFinished = MutableStateFlow(false)

    /**
     * id текущей карточки (null — сессия ещё не начата, берём первую,
     * новые сверху). Состояние — часть реактивного графа: смена карточки
     * оценкой перевычисляет UiState без пересоздания сессии (S23).
     */
    private val currentCardId = MutableStateFlow<Long?>(null)

    private val cards = repository.observeCards(deckId)
        .catch {
            // 500-аналог при чтении: сессия корректно завершается, UI не падает.
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    val uiState: StateFlow<ReviewUiState> = combine(
        cards,
        isRevealed,
        isFinished,
        currentCardId,
    ) { list, revealed, finished, currentId ->
        when {
            finished || list.isEmpty() -> ReviewUiState(isFinished = true)
            else -> {
                val index = currentId
                    ?.let { id -> list.indexOfFirst { it.id == id } }
                    ?.takeIf { it >= 0 }
                val current = if (index != null) list[index] else list.first()
                ReviewUiState(
                    front = current.front,
                    back = current.back,
                    isRevealed = revealed,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ReviewUiState(isFinished = true),
    )

    /** Переворот карточки: показ обратной стороны. */
    fun flip() {
        isRevealed.value = true
    }

    /**
     * Оценка ответа: четыре кнопки эквивалентны (влияние оценок на
     * srs.scheduling — предмет последующих изменений), любая оценка
     * переводит к следующей карточке либо завершает сессию.
     */
    fun rate() {
        isRevealed.value = false
        val list = cards.value
        val index = currentCardId.value
            ?.let { id -> list.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
            ?: 0
        val next = list.getOrNull(index + 1)
        if (next == null) {
            isFinished.value = true
        } else {
            currentCardId.value = next.id
        }
    }
}
