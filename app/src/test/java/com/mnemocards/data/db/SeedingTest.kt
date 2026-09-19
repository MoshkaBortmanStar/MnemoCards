package com.mnemocards.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Спека: prepare-sync-data-model (change-007).
 * S5 — чистая установка: БД создаётся сразу в новой схеме; идемпотентный
 *      сидинг создаёт базовую колоду из 5 карточек с клиентскими UUID,
 *      isChanged = true, revision = 1; колода видна в списке со счётчиком 5.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SeedingTest {

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

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value) }.isSuccess

    @Test
    fun cleanInstall_seedingCreatesDeckWithFiveCardsAndClientUuids() = runTest {
        DatabaseSeeder.seedIfEmpty(database)

        val decks = database.deckDao().observeDecksWithStats(1_000).first()
        assertEquals("S5: сидинг создаёт ровно одну колоду", 1, decks.size)

        val deckRow = decks.first()
        assertEquals("S5: колода видна в списке со счётчиком 5", 5, deckRow.total)

        // id записи — корректный UUID, сгенерированный на клиенте.
        assertTrue("S5: id колоды — валидный UUID", isUuid(deckRow.deckId))

        val deck = database.deckDao().getDeckByIdIncludingDeleted(deckRow.deckId)!!
        assertTrue("S5: колода isChanged = true", deck.isChanged)
        assertEquals("S5: колода revision = 1", 1, deck.revision)
        assertFalse("S5: колода isDeleted = false", deck.isDeleted)

        val cards = database.cardDao().getCardsByDeckIncludingDeleted(deckRow.deckId)
        assertEquals("S5: в колоде 5 карточек", 5, cards.size)
        assertTrue(
            "S5: id всех карточек — валидные UUID",
            cards.all { isUuid(it.id) },
        )
        assertEquals("S5: UUID карточек уникальны", 5, cards.map { it.id }.toSet().size)
        assertEquals(
            "S5: ссылка карточки на колоду — UUID колоды",
            deckRow.deckId,
            cards.first().deckId,
        )
        assertTrue("S5: карточки isChanged = true", cards.all { it.isChanged })
        assertEquals("S5: карточки revision = 1", listOf(1, 1, 1, 1, 1), cards.map { it.revision })
        assertFalse("S5: карточки isDeleted = false", cards.any { it.isDeleted })
    }

    @Test
    fun repeatedSeeding_doesNotCreateDuplicates() = runTest {
        DatabaseSeeder.seedIfEmpty(database)
        DatabaseSeeder.seedIfEmpty(database)
        DatabaseSeeder.seedIfEmpty(database)

        val decks = database.deckDao().observeDecksWithStats(1_000).first()
        assertEquals("S5: повторный сидинг не дублирует колоду", 1, decks.size)
        assertEquals("S5: счётчик карточек после повторов — 5", 5, decks.first().total)
        assertEquals(
            "S5: физических строк карточек не больше 5",
            5,
            database.cardDao().getCardsByDeckIncludingDeleted(decks.first().deckId).size,
        )
    }
}
