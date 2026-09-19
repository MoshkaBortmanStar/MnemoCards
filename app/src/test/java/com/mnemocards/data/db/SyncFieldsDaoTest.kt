package com.mnemocards.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты синхронизационных операций DAO (change-009, T1/T2): pending-отбор
 * включая tombstone (S5), физическое удаление (S7, S11), снятие pending
 * по снапшоту счётчика правок (S9, S10).
 *
 * Синх-поля — isChanged/revision/isDeleted (change-007).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncFieldsDaoTest {

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

    private fun newId(): String = UUID.randomUUID().toString()

    private fun deck(
        name: String = "Колода",
        isChanged: Boolean = false,
        revision: Int = 0,
        isDeleted: Boolean = false,
    ) = DeckEntity(
        id = newId(),
        name = name,
        isChanged = isChanged,
        revision = revision,
        isDeleted = isDeleted,
    )

    private fun card(
        deckId: String,
        front: String = "слово",
        isChanged: Boolean = false,
        revision: Int = 0,
        isDeleted: Boolean = false,
    ) = CardEntity(
        id = newId(),
        deckId = deckId,
        front = front,
        back = "перевод",
        createdAt = 0,
        isChanged = isChanged,
        revision = revision,
        isDeleted = isDeleted,
    )

    @Test
    fun `pending selection includes tombstones and excludes confirmed`() = runTest {
        val pendingDeck = deck(isChanged = true, revision = 3)
        val tombstone = deck(isChanged = true, revision = 4, isDeleted = true)
        val confirmed = deck(isChanged = false)
        deckDao.insertDeck(pendingDeck)
        deckDao.insertDeck(tombstone)
        deckDao.insertDeck(confirmed)

        val pending = deckDao.getPendingDecks().map { it.id }.toSet()
        assertEquals(setOf(pendingDeck.id, tombstone.id), pending) // S5
    }

    @Test
    fun `clear pending only when revision matches snapshot`() = runTest {
        val sent = deck(isChanged = true, revision = 5)
        val editedInFlight = deck(isChanged = true, revision = 5)
        deckDao.insertDeck(sent)
        deckDao.insertDeck(editedInFlight)

        // Правка в полёте: счётчик ушёл вперёд после снапшота (S10).
        deckDao.renameDeck(editedInFlight.id, "Новое имя")

        deckDao.clearPendingIfUnchanged(sent.id, sentRevision = 5)
        assertEquals(false, deckDao.getDeckByIdIncludingDeleted(sent.id)!!.isChanged) // S9

        deckDao.clearPendingIfUnchanged(editedInFlight.id, sentRevision = 5)
        val inFlight = deckDao.getDeckByIdIncludingDeleted(editedInFlight.id)!!
        assertTrue(inFlight.isChanged) // S10: pending сохранён
        assertEquals(6, inFlight.revision)
        assertEquals("Новое имя", inFlight.name)
    }

    @Test
    fun `physical delete removes tombstone entirely`() = runTest {
        val tombstone = deck(isChanged = true, isDeleted = true)
        deckDao.insertDeck(tombstone)

        deckDao.deleteDeckById(tombstone.id) // S7, S11

        assertNull(deckDao.getDeckByIdIncludingDeleted(tombstone.id))
    }

    @Test
    fun `alive deck ids exclude tombstones`() = runTest {
        val alive = deck()
        val dead = deck(isDeleted = true)
        deckDao.insertDeck(alive)
        deckDao.insertDeck(dead)

        assertEquals(listOf(alive.id), deckDao.getAliveDeckIds())
    }

    @Test
    fun `server update does not mark deck pending`() = runTest {
        val local = deck(isChanged = true, revision = 2)
        deckDao.insertDeck(local)

        // Применение серверной записи: перезапись без пометки (S6).
        deckDao.updateDeck(local.copy(name = "Сервер", isChanged = false))

        val updated = deckDao.getDeckByIdIncludingDeleted(local.id)!!
        assertEquals("Сервер", updated.name)
        assertEquals(false, updated.isChanged)
        assertEquals(2, updated.revision)
    }

    @Test
    fun `card pending selection and confirmation follow same rules`() = runTest {
        val deckEntity = deck()
        deckDao.insertDeck(deckEntity)
        val owner = card(deckEntity.id, isChanged = true, revision = 1)
        val tombstone = card(deckEntity.id, isChanged = true, revision = 1, isDeleted = true)
        cardDao.insertCard(owner)
        cardDao.insertCard(tombstone)

        val pending = cardDao.getPendingCards(deckEntity.id).map { it.id }.toSet()
        assertEquals(setOf(owner.id, tombstone.id), pending) // S5

        assertEquals(1, cardDao.clearPendingIfUnchanged(owner.id, sentRevision = 1)) // S9
        assertEquals(false, cardDao.getCardByIdIncludingDeleted(owner.id)!!.isChanged)

        assertEquals(1, cardDao.deleteCard(tombstone.id)) // S11
        assertNull(cardDao.getCardByIdIncludingDeleted(tombstone.id))
    }
}
