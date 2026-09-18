package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO отбора карточек в сессию, счётчика готовых и записи планирования
 * (S1, S16, S17, S22, S25, S27).
 */
@RunWith(AndroidJUnit4::class)
class CardDaoTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var cardDao: CardDao
    private lateinit var deckId: String

    private val now = 1_000_000L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        cardDao = db.cardDao()
        deckId = java.util.UUID.randomUUID().toString()
        deckDao.insertDeck(DeckEntity(id = deckId, name = "Тестовая"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertCard(
        front: String,
        dueDate: Long,
        easinessFactor: Double = 2.5,
        repetitionNumber: Int = 0,
        intervalDays: Int = 0,
    ): String {
        val id = java.util.UUID.randomUUID().toString()
        cardDao.insertCard(
            CardEntity(
                id = id,
                deckId = deckId,
                front = front,
                back = front,
                createdAt = 0L,
                easinessFactor = easinessFactor,
                repetitionNumber = repetitionNumber,
                intervalDays = intervalDays,
                dueDate = dueDate,
            ),
        )
        return id
    }

    @Test
    fun `S16 getDueCards returns only cards with dueDate le now`() = runBlocking {
        insertCard("A", dueDate = now - 86_400_000L) // вчера
        insertCard("B", dueDate = now - 3_600_000L)  // недавно
        insertCard("C", dueDate = now)                // ровно сейчас — включается
        insertCard("D", dueDate = now + 86_400_000L)  // завтра — пропускается
        insertCard("E", dueDate = now + 43_140_000L)  // сегодня 23:59 — пропускается

        val due = cardDao.getDueCards(deckId, now)

        assertEquals(listOf("A", "B", "C"), due.map { it.front })
    }

    @Test
    fun `S22 card rated Good today is not due until tomorrow`() = runBlocking {
        val ratedAt = 9 * 3_600_000L // сегодня 09:00
        val cardId = insertCard("cat", dueDate = ratedAt)
        cardDao.updateScheduling(
            cardId = cardId,
            easinessFactor = 2.5,
            repetitionNumber = 1,
            intervalDays = 1,
            dueDate = ratedAt + 86_400_000L, // завтра 09:00
        )

        // Сегодня 12:00 — карточка ещё не назрела.
        val sameDay = cardDao.getDueCards(deckId, ratedAt + 3 * 3_600_000L)
        assertTrue(sameDay.isEmpty())
        assertEquals(0, cardDao.countDue(deckId, ratedAt + 3 * 3_600_000L).first())

        // Завтра 09:01 — карточка назрела.
        val nextDay = cardDao.getDueCards(deckId, ratedAt + 86_400_000L + 60_000L)
        assertEquals(listOf("cat"), nextDay.map { it.front })
    }

    @Test
    fun `S25 countDue equals 2 when 2 of 5 cards are due`() = runBlocking {
        insertCard("A", dueDate = now - 1L)
        insertCard("B", dueDate = now - 2L)
        insertCard("C", dueDate = now + 1L)
        insertCard("D", dueDate = now + 2L)
        insertCard("E", dueDate = now + 3L)

        assertEquals(2, cardDao.countDue(deckId, now).first())
    }

    @Test
    fun `S27 countDue is zero when all cards are in the future`() = runBlocking {
        insertCard("A", dueDate = now + 1L)
        insertCard("B", dueDate = now + 2L)

        assertEquals(0, cardDao.countDue(deckId, now).first())
    }

    @Test
    fun `S1 updateScheduling persists SM-2 result`() = runBlocking {
        val cardId = insertCard("cat", dueDate = now)

        cardDao.updateScheduling(
            cardId = cardId,
            easinessFactor = 2.6,
            repetitionNumber = 1,
            intervalDays = 4,
            dueDate = now + 4L * 86_400_000L,
        )

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.6, updated.easinessFactor, 1e-9)
        assertEquals(1, updated.repetitionNumber)
        assertEquals(4, updated.intervalDays)
        assertEquals(now + 4L * 86_400_000L, updated.dueDate)
        assertFalse(updated.dueDate <= now)
    }
}
