package com.mnemocards.data.db

/**
 * Результат агрегатного запроса DAO: total — всего карточек в колоде,
 * dueToday — готовых к повторению сегодня (dueDate <= now), S25–S27.
 */
data class DeckWithCardCounts(
    val deckId: String,
    val total: Int,
    val dueToday: Int,
)
