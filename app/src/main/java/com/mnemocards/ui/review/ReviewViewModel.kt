package com.mnemocards.ui.review

import androidx.lifecycle.ViewModel
import com.mnemocards.data.HardcodedDeck
import com.mnemocards.domain.model.Grade
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * ViewModel экрана тренировки (MVVM + UDF): владеет состоянием сессии,
 * принимает события `onCardTap` / `onRate` и эмитит `StateFlow<ReviewUiState>`.
 *
 * Состояние хранится в поле ViewModel — переживает пересоздание Activity
 * при смене конфигурации (S9).
 */
class ReviewViewModel : ViewModel() {

    private val _state = MutableStateFlow(ReviewUiState())

    val state: StateFlow<ReviewUiState> = _state.asStateFlow()

    /**
     * Тап по карточке: переворот только из состояния «лицо» (S2).
     *
     * Guard-условия:
     * - повторный тап по перевёрнутой карточке состояние не меняет (S4);
     * - в состоянии finished событие игнорируется (S8).
     */
    fun onCardTap() {
        _state.update { current ->
            if (!current.isFlipped && !current.isFinished) {
                current.copy(isFlipped = true)
            } else {
                current
            }
        }
    }

    /**
     * Оценка ответа: только по перевёрнутой карточке (S2, S5) и только пока
     * сессия не завершена (S8). Индекс +1, сторона сбрасывается на «лицо» (S5),
     * после последней карточки `isFinished = true` (S7).
     *
     * Guard-условие `isFlipped` делает повторный вызов `onRate` для той же
     * карточки (double-tap) no-op'ом — переход выполняется ровно один (S12).
     *
     * S6: все четыре оценки эквивалентны — параметр `grade` намеренно ни на что
     * не влияет, SRS-планирование вне скоупа фичи.
     */
    fun onRate(grade: Grade) {
        _state.update { current ->
            if (current.isFlipped && !current.isFinished) {
                val nextIndex = current.cardIndex + 1
                current.copy(
                    cardIndex = nextIndex,
                    isFlipped = false,
                    isFinished = nextIndex >= HardcodedDeck.cards.size,
                )
            } else {
                current
            }
        }
    }
}
