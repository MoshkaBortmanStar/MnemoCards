package com.mnemocards.data.db

data class DeckWithCardCount(
    val deckId: Long,
    val deckName: String,
    val cardCount: Int,
)
