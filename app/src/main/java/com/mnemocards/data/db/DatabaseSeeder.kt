package com.mnemocards.data.db

import androidx.room.withTransaction

/**
 * Одноразовый сидинг базовой колоды (S14, S15, S19).
 *
 * Флаг «сидинг выполнен» — PRAGMA application_id в заголовке файла БД.
 * Room это поле не использует (версию схемы он хранит в user_version),
 * поэтому конфликт с миграциями исключён. Флаг переживает перезапуск
 * приложения и переоткрытие файла БД, но не воскрешает удалённые
 * данные: после удаления базовой колоды повторный запуск сидинга
 * её не создаёт. Базовая колода — обычная запись без спецпризнаков.
 */
object DatabaseSeeder {

    /** Произвольная метка «сидинг уже выполнен» (Room это поле не читает). */
    private const val SEED_MARKER = 0x4D4E454D // "MNEM"

    private const val BASE_DECK_NAME = "Английский: базовые слова"

    private val BASE_CARDS = listOf(
        "cat" to "кошка",
        "dog" to "собака",
        "house" to "дом",
        "book" to "книга",
        "water" to "вода"
    )

    /**
     * @param now текущий момент, epochMillis — dueDate карточек посева:
     *   новая карточка без истории сразу готова к повторению (S24, S29).
     */
    suspend fun seedIfEmpty(
        db: MnemoCardsDatabase,
        now: Long = System.currentTimeMillis(),
    ) {
        if (isSeedingDone(db)) return

        db.withTransaction {
            // Двойная проверка: конкурентный вызов мог засидить базу
            // между внешней проверкой и началом транзакции.
            if (isSeedingDone(db)) return@withTransaction

            val deckId = db.deckDao().insertDeck(DeckEntity(name = BASE_DECK_NAME))
            val base = now
            BASE_CARDS.forEachIndexed { index, (front, back) ->
                db.cardDao().insertCard(
                    CardEntity(
                        deckId = deckId,
                        front = front,
                        back = back,
                        // Возрастающие createdAt — детерминированный порядок посева.
                        createdAt = base + index,
                        // Параметры планирования SM-2 — дефолты новой карточки.
                        easinessFactor = 2.5,
                        repetitionNumber = 0,
                        intervalDays = 0,
                        dueDate = now,
                    )
                )
            }
            markSeedingDone(db)
        }
    }

    private fun isSeedingDone(db: MnemoCardsDatabase): Boolean =
        db.openHelper.readableDatabase
            .compileStatement("PRAGMA application_id")
            .simpleQueryForLong() == SEED_MARKER.toLong()

    private fun markSeedingDone(db: MnemoCardsDatabase) {
        db.openHelper.writableDatabase.execSQL("PRAGMA application_id = $SEED_MARKER")
    }
}
