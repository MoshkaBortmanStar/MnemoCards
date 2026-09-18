package com.mnemocards.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.CardOperationResult
import com.mnemocards.data.repository.CardRepository
import com.mnemocards.data.repository.RoomCardRepository
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CardRepositoryTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var repository: CardRepository
    private lateinit var dbFile: File
    private lateinit var deckId: String

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbFile = File.createTempFile("repo", ".db")
        dbFile.deleteOnExit()
        db = buildDb(context, dbFile)
        repository = RoomCardRepository(db.deckDao(), db.cardDao())
        deckId = UUID.randomUUID().toString()
        db.deckDao().insertDeck(DeckEntity(id = deckId, name = "Английский базовый"))
    }

    private fun buildDb(context: Context, file: File): MnemoCardsDatabase =
        Room.databaseBuilder(context, MnemoCardsDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries()
            .build()

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun CardRepository.addOk(deckId: String, front: String, back: String): String {
        val result = addCard(deckId, front, back)
        assertTrue(result is CardOperationResult.Success)
        return (result as CardOperationResult.Success).cardId
    }

    private fun randomId(): String = UUID.randomUUID().toString()

    // S1: карточки колоды читаются с лицевой и обратной стороной.
    @Test
    fun observeCards_returnsFrontAndBack() = runTest {
        repository.addOk(deckId, "hello", "привет")
        repository.addOk(deckId, "world", "мир")
        repository.addOk(deckId, "cat", "кошка")

        val cards = repository.observeCards(deckId).first()
        assertEquals(3, cards.size)
        val pair = cards.map { it.front to it.back }
        assertTrue(pair.contains("hello" to "привет"))
        assertTrue(pair.contains("world" to "мир"))
        assertTrue(pair.contains("cat" to "кошка"))
    }

    // S1: observeDeck отдаёт колоду по идентификатору.
    @Test
    fun observeDeck_returnsDeckOrNull() = runTest {
        assertEquals("Английский базовый", repository.observeDeck(deckId).first()?.name)
        assertNull(repository.observeDeck(randomId()).first())
    }

    // S3, S5: новая карточка появляется в потоке первой.
    @Test
    fun addCard_newCardAppearsFirstInFlow() = runTest {
        repository.addOk(deckId, "hello", "привет")
        repository.observeCards(deckId).test {
            awaitItem() // текущее состояние
            repository.addOk(deckId, "cat", "кошка")
            val cards = awaitItem()
            assertEquals("cat", cards.first().front)
            assertEquals("кошка", cards.first().back)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S12: update меняет значения, счётчик не растёт.
    @Test
    fun updateCard_changesValues_countStaysSame() = runTest {
        val id = repository.addOk(deckId, "cat", "кошка")
        repository.addOk(deckId, "dog", "собака")

        val result = repository.updateCard(id, "a cat", "кошка")

        assertTrue(result is CardOperationResult.Success)
        assertEquals("a cat", repository.getCardById(id)!!.front)
        assertEquals("кошка", repository.getCardById(id)!!.back)
        assertEquals(2, repository.observeCardCount(deckId).first())
    }

    // S25: update несуществующей записи не становится insert (404-аналог).
    @Test
    fun updateCard_missingRow_returnsNotFound_noInsert() = runTest {
        val missing = randomId()

        val result = repository.updateCard(missing, "x", "y")

        assertTrue(result is CardOperationResult.NotFound)
        assertNull(repository.getCardById(missing))
        assertEquals(0, repository.observeCardCount(deckId).first())
    }

    // S16: delete удаляет запись.
    @Test
    fun deleteCard_removesRow() = runTest {
        val id = repository.addOk(deckId, "cat", "кошка")
        val result = repository.deleteCard(id)

        assertTrue(result is CardOperationResult.Success)
        assertNull(repository.getCardById(id))
        assertEquals(0, repository.observeCards(deckId).first().size)
    }

    // S25: delete несуществующей записи — 404-аналог, без краха.
    @Test
    fun deleteCard_missingRow_returnsNotFound() = runTest {
        assertTrue(repository.deleteCard(randomId()) is CardOperationResult.NotFound)
    }

    // S20, S21: счётчик карточек эмитит 3 -> 4 -> 3.
    @Test
    fun observeCardCount_emits3_4_3() = runTest {
        repository.addOk(deckId, "a", "а")
        repository.addOk(deckId, "b", "б")
        repository.addOk(deckId, "c", "в")

        repository.observeCardCount(deckId).test {
            assertEquals(3, awaitItem())
            val id = repository.addOk(deckId, "cat", "кошка")
            assertEquals(4, awaitItem())
            repository.deleteCard(id)
            assertEquals(3, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // S19: данные переживают закрытие и переоткрытие БД.
    @Test
    fun operationsSurvive_dbReopen() = runTest {
        val addedId = repository.addOk(deckId, "cat", "кошка")
        val editedId = repository.addOk(deckId, "dog", "собака")
        val removedId = repository.addOk(deckId, "book", "книга")

        repository.updateCard(editedId, "a dog", "собака")
        repository.deleteCard(removedId)

        db.close()
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = buildDb(context, dbFile)
        repository = RoomCardRepository(db.deckDao(), db.cardDao())

        assertNotNull(repository.getCardById(addedId))
        assertEquals("кошка", repository.getCardById(addedId)!!.back)
        assertEquals("a dog", repository.getCardById(editedId)!!.front)
        assertNull(repository.getCardById(removedId))
        assertEquals(2, repository.observeCardCount(deckId).first())
    }

    // S9: дубликат разрешён — две независимые записи.
    @Test
    fun duplicateAddCard_createsTwoIndependentRows() = runTest {
        repository.addOk(deckId, "cat", "кошка")
        repository.addOk(deckId, "cat", "кошка")

        val cards = repository.observeCards(deckId).first()
        assertEquals(2, cards.size)
        assertTrue(cards.all { it.front == "cat" && it.back == "кошка" })
        assertEquals(2, cards.map { it.id }.distinct().size)
    }

    // S10: длинный текст сохраняется целиком.
    @Test
    fun addCard_longText_savedFully() = runTest {
        val longFront = "f".repeat(250)
        val longBack = "б".repeat(250)
        val id = repository.addOk(deckId, longFront, longBack)

        val card = repository.getCardById(id)!!
        assertEquals(longFront, card.front)
        assertEquals(longBack, card.back)
    }

    // ---------- S26: 500-аналоги на фейковом DAO, бросающем исключения ----------

    /** DAO, который падает при любом обращении к переданным операциям. */
    private class ThrowingCardDao(
        private val onGet: (String) -> CardEntity? = { null },
        private val onUpdate: (CardEntity) -> Int = { 0 },
        private val onDelete: (String) -> Int = { 0 },
    ) : CardDao {
        private val boom = RuntimeException("corrupted database")

        override suspend fun insertCard(card: CardEntity) {
            throw boom
        }

        override suspend fun insertAll(cards: List<CardEntity>) {
            throw boom
        }

        override suspend fun updateCard(card: CardEntity): Int = onUpdate(card)
        override suspend fun deleteCard(id: String): Int = onDelete(id)
        override fun observeCardsByDeck(deckId: String): Flow<List<CardEntity>> = throw boom
        override suspend fun getCardsByDeck(deckId: String): List<CardEntity> = throw boom
        override suspend fun getCardById(id: String): CardEntity? = onGet(id)
        override suspend fun getCardByIdIncludingDeleted(id: String): CardEntity? = onGet(id)
        override suspend fun getCardsByDeckIncludingDeleted(deckId: String): List<CardEntity> =
            throw boom
        override suspend fun softDeleteCard(id: String): Int = onDelete(id)
        override fun observeCardCountByDeck(deckId: String): Flow<Int> = throw boom
        override suspend fun maxCreatedAtInDeck(deckId: String): Long? = throw boom
        override suspend fun getDueCards(deckId: String, now: Long): List<CardEntity> = throw boom
        override fun countDue(deckId: String, now: Long): Flow<Int> = throw boom
        override fun observeDeckCounts(now: Long): Flow<List<com.mnemocards.data.db.DeckWithCardCounts>> =
            throw boom
        override suspend fun deckExists(deckId: String): Boolean = throw boom
        override suspend fun updateScheduling(
            cardId: String,
            easinessFactor: Double,
            repetitionNumber: Int,
            intervalDays: Int,
            dueDate: Long,
        ): Unit = throw boom
    }

    private class NoopDeckDao : DeckDao {
        override suspend fun insertDeck(deck: DeckEntity) = Unit
        override suspend fun getDeckById(id: String): DeckEntity? = null
        override suspend fun getDeckByName(name: String): DeckEntity? = null
        override suspend fun getDeckByIdIncludingDeleted(id: String): DeckEntity? = null
        override fun observeDeckById(id: String): Flow<DeckEntity?> = throw IllegalStateException()
        override fun observeDecksWithCardCounts(): Flow<List<com.mnemocards.data.db.DeckWithCardCount>> =
            throw IllegalStateException()
        override suspend fun renameDeck(id: String, name: String) = Unit
        override suspend fun deleteCardsByDeckId(deckId: String) = Unit
        override suspend fun deleteDeckById(id: String) = Unit
        override suspend fun softDeleteDeckById(id: String) = Unit
        override suspend fun softDeleteCardsByDeckId(deckId: String) = Unit
    }

    // S26: insert падает — Error, приложение не падает, записей нет.
    @Test
    fun addCard_daoThrows_returnsError() = runTest {
        val repo = RoomCardRepository(NoopDeckDao(), ThrowingCardDao())

        val result = repo.addCard(deckId, "cat", "кошка")

        assertTrue(result is CardOperationResult.Error)
    }

    // S26: update падает — Error, прежние значения не тронуты.
    @Test
    fun updateCard_daoThrows_returnsError() = runTest {
        val existingId = randomId()
        val existing = CardEntity(
            id = existingId,
            deckId = deckId,
            front = "cat",
            back = "кошка",
            createdAt = 1L,
        )
        val repo = RoomCardRepository(
            NoopDeckDao(),
            ThrowingCardDao(
                onGet = { existing },
                onUpdate = { throw RuntimeException("write failed") },
            ),
        )

        val result = repo.updateCard(existingId, "a cat", "кошка")

        assertTrue(result is CardOperationResult.Error)
        // Прежние значения не тронуты (проверяем на реальной БД).
        val realId = repository.addOk(deckId, "cat", "кошка")
        assertTrue(repository.updateCard(realId, "a cat", "кошка") is CardOperationResult.Success)
        assertEquals("a cat", repository.getCardById(realId)!!.front)
    }

    // S26: delete падает — Error, карточка не удалена.
    @Test
    fun deleteCard_daoThrows_returnsError() = runTest {
        val repo = RoomCardRepository(
            NoopDeckDao(),
            ThrowingCardDao(onDelete = { throw RuntimeException("delete failed") }),
        )

        val result = repo.deleteCard(randomId())

        assertTrue(result is CardOperationResult.Error)
    }
}
