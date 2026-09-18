package com.mnemocards.domain.model

/**
 * Доменная карточка — пара «слово + перевод» с параметрами планирования SM-2
 * (change add-sm2-scheduling). Используется выше слоя `data`; Room-сущности
 * наружу не выходят.
 */
data class Card(
    val id: String,
    val deckId: String,
    val front: String,
    val back: String,
    val easinessFactor: Double,
    val repetitionNumber: Int,
    val intervalDays: Int,
    /** Момент следующего показа, epochMillis. */
    val dueDate: Long,
)
