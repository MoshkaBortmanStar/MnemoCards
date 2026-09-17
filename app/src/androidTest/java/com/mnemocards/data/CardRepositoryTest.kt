package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.domain.scheduler.ReviewGrade
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val DAY = 86_400_000L

/**
 * Репозиторий поверх DAO и Sm2Scheduler (S1–S8, S16, S22, S25, S26):
 * отбор назревших карточек с параметрами планирования, применение оценки
 * (результат SM-2 записывается в Room) и Flow-агрегат (total, dueToday),
 * обновляющийся автоматически после оценки.
 */
@RunWith(AndroidJUnit4::class)
class CardRepositoryTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var cardDao: CardDao
    private lateinit var repository: RoomCardRepository
    private lateinit var countsRepository: RoomDeckCountsRepository
    private var deckId: Long = 0L

    private val now = 1_000_000L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        cardDao = db.cardDao()
        repository = RoomCardRepository(cardDao)
        countsRepository = RoomDeckCountsRepository(cardDao)
        deckId = deckDao.insertDeck(DeckEntity(name = "Базовая"))
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertCard(
        front: String,
        dueDate: Long = now,
        easinessFactor: Double = 2.5,
        repetitionNumber: Int = 0,
        intervalDays: Int = 0,
    ): Long = cardDao.insertCard(
        CardEntity(
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

    @Test
    fun `S16 dueCards returns only due cards with scheduling fields`() = runBlocking {
        insertCard("A", dueDate = now - 1L)
        insertCard("B", dueDate = now)
        insertCard("C", dueDate = now + DAY)

        val due = repository.dueCards(deckId, now)

        assertEquals(listOf("A", "B"), due.map { it.front })
        val first = due.first()
        assertEquals(2.5, first.easinessFactor, 1e-9)
        assertEquals(0, first.repetitionNumber)
        assertEquals(0, first.intervalDays)
        assertEquals(now - 1L, first.dueDate)
    }

    @Test
    fun `S1 applyGrade Easy on new card writes SM-2 result to Room`() = runBlocking {
        val cardId = insertCard("cat")

        val card = repository.dueCards(deckId, now).single()
        repository.applyGrade(card, ReviewGrade.EASY, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.6, updated.easinessFactor, 1e-9)
        assertEquals(1, updated.repetitionNumber)
        assertEquals(4, updated.intervalDays)
        assertEquals(now + 4L * DAY, updated.dueDate)

        // S22: карточка не возвращается в отбор до срока.
        assertTrue(repository.dueCards(deckId, now + DAY).none { it.id == cardId })
    }

    @Test
    fun `S2 applyGrade Good on new card keeps EF`() = runBlocking {
        insertCard("cat")
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.GOOD, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.5, updated.easinessFactor, 1e-9)
        assertEquals(1, updated.repetitionNumber)
        assertEquals(1, updated.intervalDays)
        assertEquals(now + DAY, updated.dueDate)
    }

    @Test
    fun `S3 applyGrade Hard on new card gives one day`() = runBlocking {
        insertCard("cat")
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.HARD, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.35, updated.easinessFactor, 1e-9)
        assertEquals(1, updated.repetitionNumber)
        assertEquals(1, updated.intervalDays)
        assertEquals(now + DAY, updated.dueDate)
    }

    @Test
    fun `S4 applyGrade Forgot resets repetition and delays by 10 minutes`() = runBlocking {
        insertCard("cat")
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.FORGOT, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.3, updated.easinessFactor, 1e-9)
        assertEquals(0, updated.repetitionNumber)
        assertEquals(0, updated.intervalDays)
        assertEquals(now + 10L * 60_000L, updated.dueDate)
    }

    @Test
    fun `S5 applyGrade Good on repeating card multiplies interval by EF`() = runBlocking {
        insertCard("dog", repetitionNumber = 2, intervalDays = 6)
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.GOOD, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.5, updated.easinessFactor, 1e-9)
        assertEquals(3, updated.repetitionNumber)
        assertEquals(15, updated.intervalDays)
        assertEquals(now + 15L * DAY, updated.dueDate)
    }

    @Test
    fun `S6 applyGrade Easy on repeating card applies bonus`() = runBlocking {
        insertCard("dog", repetitionNumber = 2, intervalDays = 6)
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.EASY, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.6, updated.easinessFactor, 1e-9)
        assertEquals(20, updated.intervalDays)
        assertEquals(now + 20L * DAY, updated.dueDate)
    }

    @Test
    fun `S7 applyGrade Hard on repeating card uses 1_2 factor`() = runBlocking {
        insertCard("dog", repetitionNumber = 2, intervalDays = 6)
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.HARD, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.35, updated.easinessFactor, 1e-9)
        assertEquals(7, updated.intervalDays)
        assertEquals(now + 7L * DAY, updated.dueDate)
    }

    @Test
    fun `S8 applyGrade Forgot on repeating card resets progress`() = runBlocking {
        insertCard("dog", repetitionNumber = 2, intervalDays = 6)
        val card = repository.dueCards(deckId, now).single()

        repository.applyGrade(card, ReviewGrade.FORGOT, now)

        val updated = cardDao.getCardsByDeck(deckId).single()
        assertEquals(2.3, updated.easinessFactor, 1e-9)
        assertEquals(0, updated.repetitionNumber)
        assertEquals(0, updated.intervalDays)
        assertEquals(now + 10L * 60_000L, updated.dueDate)
    }

    @Test
    fun `S25 S26 observeDecksWithCounts emits total and dueToday and updates after grade`() = runBlocking {
        insertCard("A", dueDate = now - 1L)
        insertCard("B", dueDate = now)
        insertCard("C", dueDate = now + DAY)
        insertCard("D", dueDate = now + 2L * DAY)
        insertCard("E", dueDate = now + 3L * DAY)

        val before = countsRepository.observeDecksWithCounts(now).first()
        assertEquals(5, before.first().total)
        assertEquals(2, before.first().dueToday)

        // Обе назревшие карточки получают graduating-оценку — due уходит в будущее.
        val dueCards = repository.dueCards(deckId, now)
        repository.applyGrade(dueCards[0], ReviewGrade.HARD, now)
        repository.applyGrade(dueCards[1], ReviewGrade.GOOD, now)

        val after = countsRepository.observeDecksWithCounts(now).first()
        assertEquals(5, after.first().total)
        assertEquals(0, after.first().dueToday)
    }
}
