package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты RoomDeckRepository (S1, S8, S12, S21, S22): создание,
 * переименование, каскадное удаление, no-op по отсутствующему id,
 * проброс ошибок БД наружу.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomDeckRepositoryTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var repository: RoomDeckRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        repository = RoomDeckRepository(deckDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun insertCard(deckId: Long, front: String, back: String) {
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO cards (deckId, front, back) VALUES (?, ?, ?)",
            arrayOf(deckId, front, back)
        )
    }

    private fun cardCount(deckId: Long): Int {
        db.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM cards WHERE deckId = ?",
            arrayOf(deckId.toString())
        ).use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    // S1: createDeck пишет строку и возвращает id; название обрезается trim
    @Test
    fun createDeck_insertsRowAndReturnsId_withTrimmedName() = runTest {
        val id = repository.createDeck("  Английские глаголы  ")

        assertTrue(id > 0)
        assertEquals("Английские глаголы", deckDao.getDeckById(id)?.name)
    }

    // S8: renameDeck обновляет существующую запись
    @Test
    fun renameDeck_updatesExistingRow() = runTest {
        val id = repository.createDeck("Немецкий")

        repository.renameDeck(id, "Немецкий B1")

        assertEquals("Немецкий B1", deckDao.getDeckById(id)?.name)
        val decks = repository.observeDecks().first()
        assertEquals(1, decks.size)
        assertEquals("Немецкий B1", decks.first().name)
    }

    // S12: deleteDeck удаляет колоду и все её карточки
    @Test
    fun deleteDeck_removesDeckAndItsCards() = runTest {
        val englishId = repository.createDeck("Английский")
        val germanId = repository.createDeck("Немецкий")
        repeat(3) { insertCard(englishId, "f$it", "b$it") }
        repeat(5) { insertCard(germanId, "v$it", "r$it") }

        repository.deleteDeck(englishId)

        val decks = repository.observeDecks().first()
        assertEquals(1, decks.size)
        assertEquals("Немецкий", decks.first().name)
        assertEquals(5, decks.first().cardCount)
        assertEquals(0, cardCount(englishId))
    }

    // S21 (аналог 404): несуществующий id — безопасный no-op
    @Test
    fun operationsWithMissingId_areSafeNoOp() = runTest {
        val id = repository.createDeck("Колода")
        insertCard(id, "a", "b")

        repository.renameDeck(999L, "Призрак")
        repository.deleteDeck(999L)

        assertEquals("Колода", deckDao.getDeckById(id)?.name)
        assertEquals(1, repository.observeDecks().first().size)
        assertEquals(1, cardCount(id))
    }

    // S22 (аналог 500): исключение DAO пробрасывается наружу
    @Test
    fun daoFailure_isPropagated() = runTest {
        val failingRepository = RoomDeckRepository(FailingDeckDao(deckDao))

        val createError = runCatching { failingRepository.createDeck("A") }.exceptionOrNull()
        val renameError = runCatching { failingRepository.renameDeck(1L, "B") }.exceptionOrNull()
        val deleteError = runCatching { failingRepository.deleteDeck(1L) }.exceptionOrNull()

        assertTrue(createError is RuntimeException)
        assertTrue(renameError is RuntimeException)
        assertTrue(deleteError is RuntimeException)
    }

    /** Фейковый DAO: все мутации бросают RuntimeException (имитация сбоя диска). */
    private class FailingDeckDao(private val original: DeckDao) : DeckDao by original {
        override suspend fun insertDeck(deck: DeckEntity): Long =
            throw RuntimeException("disk error")

        override suspend fun renameDeck(id: Long, name: String): Unit =
            throw RuntimeException("disk error")

        override suspend fun deleteCardsByDeckId(deckId: Long): Unit =
            throw RuntimeException("disk error")

        override suspend fun deleteDeckById(id: Long): Unit =
            throw RuntimeException("disk error")

        /** Делегирование `by` пробрасывает и default-методы — гасим и его. */
        override suspend fun deleteDeckWithCards(id: Long): Unit =
            throw RuntimeException("disk error")
    }
}
