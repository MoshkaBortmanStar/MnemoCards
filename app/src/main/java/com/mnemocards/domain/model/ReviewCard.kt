package com.mnemocards.domain.model

/**
 * Доменная модель карточки сессии повторения `cards.review` (S5):
 * пара «слово + перевод», загружаемая из Room
 * по идентификатору выбранной колоды.
 */
data class ReviewCard(
    val id: Long,
    val front: String,
    val back: String,
)
