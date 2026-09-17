package com.mnemocards.domain.model

/**
 * Доменная модель колоды для слоёв выше data.
 * Room-сущности (`DeckEntity`) не покидают пакет `data`.
 */
data class Deck(
    val id: Long,
    val name: String,
    val cardCount: Int
)
