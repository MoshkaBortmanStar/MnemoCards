package com.mnemocards.domain.model

/**
 * Доменная модель колоды для стартового экрана (S3):
 * идентификатор, название и количество карточек в колоде.
 *
 * Является проекцией данных Room ([com.mnemocards.data.db.DeckWithCardCount]),
 * UI-слой не знает о сущностях БД.
 */
data class Deck(
    val id: Long,
    val name: String,
    val cardCount: Int,
)
