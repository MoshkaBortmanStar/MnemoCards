package com.mnemocards.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.flow.first
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
 * Спека: prepare-sync-data-model (change-007).
 * S21 — мягко удалённые записи скрыты со всех экранов, счётчиков и экспорта.
 * S22 — мягко удалённые карточки не участвуют в тренировке.
 * S23 — мягко удалённая запись доступна по прямому запросу к DAO.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaoFilterTest {

    private lateinit var database: MnemoCardsDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun insertDeck(id: String, name: String = "Deck") {
        database.deckDao().insertDeck(
            DeckEntity(
                id = id,
                name = name,
                isChanged = true,
                revision = 1,
                isDeleted = false,
            ),
        )
    }

    private suspend fun insertCard(id: String, deckId: String, front: String) {
        database.cardDao().insertCard(
            CardEntity(
                id = id,
                deckId = deckId,
                front = front,
                back = "back",
                createdAt = 0,
                easinessFactor = 2.5,
                repetitionNumber = 0,
                intervalDays = 0,
                // dueDate = 0: карточка назрела, попадёт в отбор сессии.
                dueDate = 0,
                isChanged = true,
                revision = 1,
                isDeleted = false,
            ),
        )
    }

    // --- S21: список колод и счётчики не учитывают мягко удалённые ---

    @Test
    fun deckListCounts_excludeSoftDeletedCardsAndDecks() = runTest {
        val deckA = UUID.randomUUID().toString()
        val deckDeleted = UUID.randomUUID().toString()
        insertDeck(deckA, "A")
        insertDeck(deckDeleted, "Deleted")

        // 5 карточек в колоде A, 2 из них мягко удалены.
        val active = (1..3).map { UUID.randomUUID().toString() }
        val softDeleted = (1..2).map { UUID.randomUUID().toString() }
        (active + softDeleted).forEachIndexed { index, id ->
            insertCard(id, deckA, "card$index")
        }
        database.cardDao().softDeleteCard(softDeleted[0])
        database.cardDao().softDeleteCard(softDeleted[1])

        // Мягко удаляем вторую колоду — её не должно быть в списке.
        database.deckDao().softDeleteDeckById(deckDeleted)

        val decks = database.deckDao().observeDecksWithStats(1_000).first()

        assertEquals("S21: в списке только неудалённая колода", listOf(deckA), decks.map { it.deckId })
        val row = decks.first()
        assertEquals("S21: счётчик колоды A — 3, а не 5", 3, row.total)
    }

    // --- S21: агрегат (total, dueToday) без мягко удалённых ---

    @Test
    fun aggregateCounts_excludeSoftDeleted() = runTest {
        val deckA = UUID.randomUUID().toString()
        insertDeck(deckA, "A")
        val cardA = UUID.randomUUID().toString()
        val cardB = UUID.randomUUID().toString()
        insertCard(cardA, deckA, "a")
        insertCard(cardB, deckA, "b")
        database.cardDao().softDeleteCard(cardB)

        val counts = database.deckDao().observeDecksWithStats(now = 1_000).first()

        val row = counts.first { it.deckId == deckA }
        assertEquals("S21: total — только живые карточки", 1, row.total)
        assertEquals("S21: dueToday — только живые карточки", 1, row.dueToday)
    }

    // --- S21: экспортный запрос без мягко удалённых ---

    @Test
    fun exportQuery_excludeSoftDeletedCards() = runTest {
        val deckA = UUID.randomUUID().toString()
        insertDeck(deckA, "A")
        val keep = UUID.randomUUID().toString()
        val remove = UUID.randomUUID().toString()
        insertCard(keep, deckA, "keep")
        insertCard(remove, deckA, "remove")
        database.cardDao().softDeleteCard(remove)

        val exported = database.cardDao().getCardsByDeck(deckA)

        assertEquals(
            "S21: экспорт не содержит мягко удалённые карточки",
            listOf(keep),
            exported.map { it.id },
        )
    }

    // --- S22: карточки для сессии тренировки без мягко удалённых ---

    @Test
    fun sessionCardsQuery_excludesSoftDeleted() = runTest {
        val deckA = UUID.randomUUID().toString()
        insertDeck(deckA, "A")
        val cardIds = (1..6).map { UUID.randomUUID().toString() }
        cardIds.forEachIndexed { index, id -> insertCard(id, deckA, "card$index") }
        database.cardDao().softDeleteCard(cardIds[0])

        val sessionCards = database.cardDao().getDueCards(deckA, now = 1_000)

        assertEquals("S22: сессия состоит из 5 карточек", 5, sessionCards.size)
        assertTrue(
            "S22: мягко удалённая карточка не показывается",
            sessionCards.none { it.id == cardIds[0] },
        )
    }

    // --- S9: мягко удалённая колода неотличима от отсутствующей ---

    @Test
    fun softDeletedDeck_behavesAsMissing() = runTest {
        val deckA = UUID.randomUUID().toString()
        insertDeck(deckA, "A")
        database.deckDao().softDeleteDeckById(deckA)

        assertTrue("S9: deckExists = false для мягко удалённой", !database.cardDao().deckExists(deckA))
        assertNull("S9: живой запрос колоды — null", database.deckDao().getDeckById(deckA))
        assertNotNull(
            "S23: прямой запрос без фильтра находит строку",
            database.deckDao().getDeckByIdIncludingDeleted(deckA),
        )
    }

    // --- S23: прямой запрос по-прежнему находит мягко удалённую запись ---

    @Test
    fun getById_withoutFilter_findsSoftDeletedRow() = runTest {
        val deckA = UUID.randomUUID().toString()
        insertDeck(deckA, "A")
        val cardId = UUID.randomUUID().toString()
        insertCard(cardId, deckA, "front")
        database.cardDao().softDeleteCard(cardId)

        val card = database.cardDao().getCardByIdIncludingDeleted(cardId)
        assertNotNull("S23: карточка найдена прямым запросом", card)
        assertTrue("S23: isDeleted = true у найденной карточки", card!!.isDeleted)
        assertEquals("S23: исходные данные сохранены", "front", card.front)
        assertEquals("S19: revision наращен при мягком удалении", 2, card.revision)
        assertTrue("S19: isChanged выставлен", card.isChanged)
    }
}
