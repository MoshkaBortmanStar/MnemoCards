package com.mnemocards.data

import com.mnemocards.domain.model.Card

/**
 * Захардкоженная колода для первой версии экрана тренировки (S1).
 *
 * Свойства данных:
 * - ровно 5 карточек, порядок фиксирован этим списком и не меняется (S6);
 * - письменности смешанные: латиница, кириллица, CJK (S10),
 *   конкретная языковая пара не зашита;
 * - одна карточка со словом и переводом длиннее 40 символов (S11).
 */
object HardcodedDeck {

    val cards: List<Card> = listOf(
        Card(front = "cat", back = "кошка"),
        Card(front = "собака", back = "dog"),
        Card(front = "book", back = "本"),
        Card(front = "ありがとう", back = "спасибо"),
        Card(
            front = "The quick brown fox jumps over the lazy dog",
            back = "Быстрая бурая лиса перепрыгивает через ленивого пса",
        ),
    )
}
