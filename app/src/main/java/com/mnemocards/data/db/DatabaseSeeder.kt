package com.mnemocards.data.db

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Идемпотентный сидинг базы при первом запуске (S1, S2, S11).
 *
 * Если таблица колод пуста, транзакционно создаётся базовая колода
 * с теми же пятью парами «слово + перевод», что были в захардкоженной
 * версии `cards.review` (change `add-review-screen`). Повторные вызовы
 * (перезапуск приложения, пересоздание Activity) не создают дублей —
 * guard по пустоте таблицы колод внутри транзакции DAO.
 */
class DatabaseSeeder(
    private val deckDao: DeckDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun seedIfNeeded() = withContext(ioDispatcher) {
        deckDao.insertDeckWithCardsIfAbsent(
            deck = DeckEntity(name = BASE_DECK_NAME),
            cards = BASE_CARDS.map { (front, back) ->
                CardEntity(deckId = 0L, front = front, back = back)
            },
        )
    }

    companion object {

        const val BASE_DECK_NAME = "Базовая колода"

        /**
         * Те же пять пар «слово + перевод», что были в захардкоженной
         * колоде экрана `cards.review`.
         */
        val BASE_CARDS: List<Pair<String, String>> = listOf(
            "cat" to "кошка",
            "dog" to "собака",
            "house" to "дом",
            "water" to "вода",
            "book" to "книга",
        )
    }
}
