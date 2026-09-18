package com.mnemocards.domain.model

/**
 * Пара счётчиков для элемента списка колод (S25–S27): общее количество
 * карточек и сколько из них готово к повторению сегодня.
 */
data class DeckCounts(
    val deckId: String,
    val total: Int,
    val dueToday: Int,
)
