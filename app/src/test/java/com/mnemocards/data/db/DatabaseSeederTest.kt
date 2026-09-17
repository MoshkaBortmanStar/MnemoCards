package com.mnemocards.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты идемпотентного сидинга базовой колоды.
 *
 * Покрывает сценарии spec-bdd:
 * - первый запуск создаёт ровно одну колоду с 5 карточками;
 * - повторный запуск не создаёт дубликатов (S2/S11 change-002):
 *   сидинг выполняется по флагу «колода с таким именем существует»,
 *   пересоздание Activity/повторные вызовы безопасны.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseSeederTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    /**
     * GIVEN локальная база пуста (первый запуск).
     * WHEN выполняется сидинг.
     * THEN в БД ровно одна колода «Английский базовый» с 5 карточками,
     * список колод отдаёт название и счётчик карточек «5».
     */
    @Test
    fun firstSeed_createsSingleDeckWithFiveCards() = runTest {
        DatabaseSeeder.seed(db)

        val deck = deckDao.getDeckByName(BASE_DECK_NAME)
        assertNotNull("базовая колода должна быть создана", deck)

        val counts = deckDao.observeDecksWithCardCounts().first()
        assertEquals(1, counts.size)
        assertEquals(BASE_DECK_NAME, counts[0].deckName)
        assertEquals(5, counts[0].cardCount)
    }

    /**
     * GIVEN базовая колода уже засеяна.
     * WHEN сидинг вызывается повторно (перезапуск, пересоздание
     * Activity, гонка Application/MainActivity).
     * THEN дубликат не создаётся: колода одна, карточек по-прежнему 5.
     */
    @Test
    fun repeatedSeed_doesNotDuplicateDeckOrCards() = runTest {
        DatabaseSeeder.seed(db)
        DatabaseSeeder.seed(db)
        DatabaseSeeder.seed(db)

        val counts = deckDao.observeDecksWithCardCounts().first()
        assertEquals(1, counts.size)
        assertEquals(5, counts[0].cardCount)
    }

    private companion object {
        const val BASE_DECK_NAME = "Английский базовый"
    }
}
