package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.DatabaseSeeder
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S24, S29: идемпотентный сидинг базовой колоды с параметрами планирования
 * по умолчанию — EF=2.5, №0, интервал 0, dueDate=«сейчас» (все 5 карточек
 * готовы к повторению сегодня); повторный запуск не дублирует данные.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSeederTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var cardDao: CardDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        cardDao = db.cardDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `S29 first run seeds 5 cards with scheduling defaults`() = runBlocking {
        val before = System.currentTimeMillis()
        DatabaseSeeder.seedIfEmpty(db)
        val after = System.currentTimeMillis()

        val cards = cardDao.getCardsByDeck(deckId = 1L)
        assertEquals(5, cards.size)
        cards.forEach { card ->
            assertEquals(2.5, card.easinessFactor, 1e-9)
            assertEquals(0, card.repetitionNumber)
            assertEquals(0, card.intervalDays)
            // dueDate = «сейчас»: новая карточка готова к показу немедленно (S24).
            assertTrue("dueDate должно быть в окне сидинга", card.dueDate in before..after)
        }
    }

    @Test
    fun `S29 second run does not duplicate seeded data`() = runBlocking {
        DatabaseSeeder.seedIfEmpty(db)
        DatabaseSeeder.seedIfEmpty(db)

        val cards = cardDao.getCardsByDeck(deckId = 1L)
        assertEquals(5, cards.size)
    }
}
