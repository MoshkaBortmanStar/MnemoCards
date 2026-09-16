package com.mnemocards.domain.model

/**
 * Карточка — пара «слово — перевод» (S1).
 *
 * Текст обеих сторон отображается «как есть», без транслитерации
 * и трансформации — письменности произвольные (S10).
 */
data class Card(
    val front: String,
    val back: String,
)
