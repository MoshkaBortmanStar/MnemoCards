package com.mnemocards.data.db

data class DeckWithCardCount(
    val deckId: String,
    val deckName: String,
    val cardCount: Int,
)
