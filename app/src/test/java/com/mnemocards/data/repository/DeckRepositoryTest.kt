package com.mnemocards.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.RoomDeckRepository
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Спека: prepare-sync-data-model (change-007).
 * S6, S10, S13 — создание записей с UUID и признаками.
 * S11, S13 — изменение записи выставляет isChanged и поднимает revision;
 *            признаки переживают переоткрытие БД и не снимаются (S12).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeckRepositoryTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckRepository: RoomDeckRepository
    private lateinit var cardRepository: RoomCardRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("deck-repo-test.db")
        db = Room.databaseBuilder(
            context,
            MnemoCardsDatabase::class.java,
            "deck-repo-test.db",
        ).allowMainThreadQueries().build()
        deckRepository = RoomDeckRepository(db.deckDao())
        cardRepository = RoomCardRepository(db.deckDao(), db.cardDao())
    }

    @After
    fun tearDown() {
        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("deck-repo-test.db")
    }

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value) }.isSuccess

    /** Создаёт колоду через репозиторий и возвращает её UUID из списка. */
    private suspend fun createDeckViaRepository(name: String): String {
        deckRepository.createDeck(name)
        return db.deckDao().observeDecksWithStats(1_000).first()
            .first { it.deckName == name }.deckId
    }

    // --- S6, S10, S13: создание колоды ---

    @Test
    fun createDeck_generatesValidUuid_andSyncFlags() = runTest {
        val deckId = createDeckViaRepository("English")

        assertTrue("id должен быть валидным UUID", isUuid(deckId))

        val entity = db.deckDao().getDeckByIdIncludingDeleted(deckId)!!
        assertEquals("S10: isChanged = true при создании", true, entity.isChanged)
        assertEquals("S13: revision = 1 при создании", 1, entity.revision)
        assertEquals("isDeleted = false при создании", false, entity.isDeleted)
    }

    @Test
    fun createDeck_idsAreUnique() = runTest {
        val first = createDeckViaRepository("English")
        val second = createDeckViaRepository("Spanish")

        assertNotEquals("UUID колод должны быть уникальны", first, second)
    }

    // --- S6, S10, S13: создание карточки ---

    @Test
    fun createCard_usesDeckUuid_andSyncFlags() = runTest {
        val deckId = createDeckViaRepository("English")

        val result = cardRepository.addCard(deckId, "cat", "кот")
        val cardId = (result as CardOperationResult.Success).cardId

        assertTrue("id карточки должен быть валидным UUID", isUuid(cardId))

        val entity = db.cardDao().getCardByIdIncludingDeleted(cardId)!!
        assertEquals("ссылка карточки на колоду — UUID колоды", deckId, entity.deckId)
        assertEquals("S10: isChanged = true при создании", true, entity.isChanged)
        assertEquals("S13: revision = 1 при создании", 1, entity.revision)
        assertEquals("isDeleted = false при создании", false, entity.isDeleted)
    }

    @Test
    fun createCard_idsAreUniqueWithinDeck() = runTest {
        val deckId = createDeckViaRepository("English")

        val first = (cardRepository.addCard(deckId, "cat", "кот") as CardOperationResult.Success).cardId
        val second = (cardRepository.addCard(deckId, "dog", "пёс") as CardOperationResult.Success).cardId

        assertNotEquals("UUID карточек должны быть уникальны", first, second)
    }

    // --- S11, S13: изменение карточки ---

    @Test
    fun updateCard_setsIsChanged_andIncrementsRevision() = runTest {
        val deckId = UUID.randomUUID().toString()
        db.deckDao().insertDeck(
            DeckEntity(deckId, "English", isChanged = true, revision = 1, isDeleted = false),
        )
        // GIVEN: карточка с isChanged = false, revision = 3, установлено напрямую.
        val cardId = UUID.randomUUID().toString()
        db.cardDao().insertCard(
            CardEntity(
                id = cardId,
                deckId = deckId,
                front = "cat",
                back = "кот",
                createdAt = 0,
                easinessFactor = 2.5,
                repetitionNumber = 0,
                intervalDays = 0,
                dueDate = 0,
                isChanged = false,
                revision = 3,
                isDeleted = false,
            ),
        )

        // WHEN: первое редактирование.
        cardRepository.updateCard(cardId, "cat!", "кошка")

        var entity = db.cardDao().getCardByIdIncludingDeleted(cardId)!!
        assertEquals("S11: isChanged = true после редактирования", true, entity.isChanged)
        assertEquals("S13: revision = 4 после первого изменения", 4, entity.revision)
        assertEquals("кошка", entity.back)

        // WHEN: второе редактирование.
        cardRepository.updateCard(cardId, "cat!", "кошак")

        entity = db.cardDao().getCardByIdIncludingDeleted(cardId)!!
        assertEquals("S13: revision = 5 после второго изменения", 5, entity.revision)
        assertEquals(true, entity.isChanged)
        assertEquals("кошак", entity.back)
    }

    // --- S11, S13: переименование колоды ---

    @Test
    fun renameDeck_setsIsChanged_andIncrementsRevision() = runTest {
        val deckId = UUID.randomUUID().toString()
        db.deckDao().insertDeck(
            DeckEntity(deckId, "English", isChanged = false, revision = 2, isDeleted = false),
        )

        deckRepository.renameDeck(deckId, "English B2")

        val entity = db.deckDao().getDeckByIdIncludingDeleted(deckId)!!
        assertEquals("S11: isChanged = true после редактирования", true, entity.isChanged)
        assertEquals("S13: revision = 3", 3, entity.revision)
        assertEquals("English B2", entity.name)
    }

    // --- S12: признаки переживают переоткрытие БД ---

    @Test
    fun syncFlags_surviveDatabaseReopen() = runTest {
        val deckId = UUID.randomUUID().toString()
        val cardId = UUID.randomUUID().toString()
        db.deckDao().insertDeck(
            DeckEntity(deckId, "English", isChanged = true, revision = 1, isDeleted = false),
        )
        db.cardDao().insertCard(
            CardEntity(
                id = cardId,
                deckId = deckId,
                front = "cat",
                back = "кот",
                createdAt = 0,
                easinessFactor = 2.5,
                repetitionNumber = 0,
                intervalDays = 0,
                dueDate = 0,
                isChanged = true,
                revision = 2,
                isDeleted = false,
            ),
        )

        // WHEN: закрыть приложение (БД) и открыть заново — новое соединение.
        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.databaseBuilder(context, MnemoCardsDatabase::class.java, "deck-repo-test.db")
            .allowMainThreadQueries().build()

        // THEN: признаки не сброшены.
        val deck = db.deckDao().getDeckByIdIncludingDeleted(deckId)!!
        assertEquals(true, deck.isChanged)
        assertEquals(1, deck.revision)
        val card = db.cardDao().getCardByIdIncludingDeleted(cardId)!!
        assertEquals(true, card.isChanged)
        assertEquals(2, card.revision)
        assertEquals(false, card.isDeleted)
    }
}
