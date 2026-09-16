package com.mnemocards.ui.review

import com.mnemocards.data.HardcodedDeck
import com.mnemocards.domain.model.Card

/**
 * UDF-состояние экрана тренировки (S1).
 *
 * - `cardIndex` — индекс текущей карточки в захардкоженной колоде (S1, S6);
 * - `isFlipped` — сторона карточки: `false` — «лицо» (слово), `true` — «перевод» (S2);
 * - `isFinished` — сессия завершена, показывается экран «на сегодня всё» (S7, S8).
 *
 * Состояние живёт в `ReviewViewModel` (S9), а не в `remember` внутри композиции.
 * `currentCard` — вычисляемое свойство: текущая карточка из `HardcodedDeck.cards`
 * с фиксированным порядком (S6); после завершения сессии индекс выходит за
 * границы списка, поэтому возвращается последняя карточка колоды.
 */
data class ReviewUiState(
    val cardIndex: Int = 0,
    val isFlipped: Boolean = false,
    val isFinished: Boolean = false,
) {
    val currentCard: Card
        get() = HardcodedDeck.cards.getOrElse(cardIndex) { HardcodedDeck.cards.last() }
}
