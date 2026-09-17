package com.mnemocards.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты мутаций DeckDao: вставка, переименование, каскадное удаление,
 * сосуществование дубликатов названий (S1, S4, S8, S12, S20).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeckDaoTest {

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

    // --- Вспомогательные операции над карточками через raw SQL,
    // --- чтобы тест DeckDao не зависел от методов CardDao.

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

    private fun deckRowCount(): Int {
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM decks").use { cursor ->
            cursor.moveToFirst()
            return cursor.getInt(0)
        }
    }

    private fun deckName(id: Long): String? {
        db.openHelper.readableDatabase.query(
            "SELECT name FROM decks WHERE id = ?",
            arrayOf(id.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
            return null
        }
    }

    // S1: вставка колоды → появилась строка в таблице, возвращён id
    @Test
    fun insertDeck_createsRowAndReturnsId() = runTest {
        val id = deckDao.insertDeck(DeckEntity(name = "Английские глаголы"))

        assertTrue(id > 0)
        assertEquals(1, deckRowCount())
        assertEquals("Английские глаголы", deckName(id))
    }

    // S8: переименование по id — обновлена та же строка, новых строк нет,
    // карточки сохранили привязку
    @Test
    fun renameDeck_updatesSameRowAndKeepsCards() = runTest {
        val id = deckDao.insertDeck(DeckEntity(name = "Немецкий"))
        insertCard(id, "Haus", "дом")
        insertCard(id, "Buch", "книга")

        deckDao.renameDeck(id, "Немецкий B1")

        assertEquals(1, deckRowCount())
        assertEquals("Немецкий B1", deckName(id))
        assertEquals(2, cardCount(id))
    }

    // S12: удаление колоды удаляет все её карточки,
    // карточки других колод не затронуты
    @Test
    fun deleteDeckWithCards_removesDeckAndItsCardsOnly() = runTest {
        val englishId = deckDao.insertDeck(DeckEntity(name = "Английский"))
        val germanId = deckDao.insertDeck(DeckEntity(name = "Немецкий"))
        repeat(3) { insertCard(englishId, "front$it", "back$it") }
        repeat(5) { insertCard(germanId, "vorderseite$it", "rückseite$it") }

        deckDao.deleteDeckWithCards(englishId)

        assertEquals(1, deckRowCount())
        assertEquals("Немецкий", deckName(germanId))
        assertEquals(0, cardCount(englishId))
        assertEquals(5, cardCount(germanId))
    }

    // S4/S20: две колоды с одинаковым названием сосуществуют,
    // удаление одной не влияет на другую
    @Test
    fun duplicateNames_coexistAndDeleteIsIndependent() = runTest {
        val firstId = deckDao.insertDeck(DeckEntity(name = "Немецкий"))
        val secondId = deckDao.insertDeck(DeckEntity(name = "Немецкий"))
        insertCard(firstId, "Haus", "дом")
        repeat(5) { insertCard(secondId, "w$it", "t$it") }

        assertTrue(firstId != secondId)
        assertEquals(2, deckRowCount())

        deckDao.deleteDeckWithCards(secondId)

        assertEquals(1, deckRowCount())
        assertEquals("Немецкий", deckName(firstId))
        assertEquals(1, cardCount(firstId))
    }

    // S21 (аналог 404): операции с несуществующим id — безопасный no-op
    @Test
    fun mutationsWithMissingId_areSafeNoOp() = runTest {
        val id = deckDao.insertDeck(DeckEntity(name = "Колода"))
        insertCard(id, "a", "b")

        deckDao.renameDeck(999L, "Призрак")
        deckDao.deleteDeckWithCards(999L)

        assertEquals(1, deckRowCount())
        assertEquals("Колода", deckName(id))
        assertEquals(1, cardCount(id))
        assertNotNull(deckDao.getDeckById(id))
    }
}
