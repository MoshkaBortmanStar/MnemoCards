package com.mnemocards.data.db

/**
 * Идемпотентный сидинг базовой колоды «Английский базовый» из 5 карточек.
 * Повторный вызов не создаёт дубликатов: сид выполняется только если
 * колоды с таким именем ещё нет.
 */
object DatabaseSeeder {

    private const val BASE_DECK_NAME = "Английский базовый"

    private val SEED_CARDS = listOf(
        "hello" to "привет",
        "world" to "мир",
        "cat" to "кошка",
        "dog" to "собака",
        "book" to "книга",
    )

    suspend fun seed(db: MnemoCardsDatabase) {
        val deckDao = db.deckDao()
        val cardDao = db.cardDao()
        if (deckDao.getDeckByName(BASE_DECK_NAME) != null) return

        val deckId = deckDao.insertDeck(DeckEntity(name = BASE_DECK_NAME))
        val base = System.currentTimeMillis()
        SEED_CARDS.forEachIndexed { index, (front, back) ->
            cardDao.insertCard(
                CardEntity(
                    deckId = deckId,
                    front = front,
                    back = back,
                    // Возрастающие createdAt — детерминированный порядок посева.
                    createdAt = base + index,
                )
            )
        }
    }
}
