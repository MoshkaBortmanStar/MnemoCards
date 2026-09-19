package com.mnemocards.data.db

/**
 * Строка списка колод: единая проекция всех данных, нужных экрану, —
 * идентификатор, название, всего живых карточек (total) и сколько из
 * них готово к повторению сегодня (dueToday, dueDate <= now).
 *
 * change-007: заменила пару DeckWithCardCount / DeckWithCardCounts —
 * имена различались одной буквой при разном назначении.
 */
data class DeckWithStats(
    val deckId: String,
    val deckName: String,
    val total: Int,
    val dueToday: Int,
)
