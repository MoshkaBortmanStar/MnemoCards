package com.mnemocards.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.RoomDeckRepository
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.sync.SyncFlagDataStore
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Спека: prepare-sync-data-model (change-007).
 * S17 — физическое удаление карточки при выключенном флаге.
 * S18 — физическое удаление колоды каскадно удаляет все её карточки.
 * S16 — ошибка чтения DataStore трактуется как выключенный флаг,
 *        удаление выполняется физическое.
 * S19 — мягкое удаление карточки при включённом флаге.
 * S20 — мягкое удаление колоды каскадно помечает карточки.
 * S23 — мягко удалённая запись доступна по прямому запросу к DAO.
 * S24 — переключение флага не трогает мягко удалённые записи.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeletionRepositoryTest {

    @get:Rule
    val tmpFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var database: MnemoCardsDatabase
    private lateinit var deckRepository: RoomDeckRepository
    private lateinit var cardRepository: RoomCardRepository

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MnemoCardsDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        deckRepository = RoomDeckRepository(database.deckDao())
        cardRepository = RoomCardRepository(database.deckDao(), database.cardDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun newDeckId(): String = UUID.randomUUID().toString()

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

    private suspend fun insertCard(
        id: String,
        deckId: String,
        front: String = "front",
        revision: Int = 1,
    ) {
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
                dueDate = 0,
                isChanged = true,
                revision = revision,
                isDeleted = false,
            ),
        )
    }

    /** DataStore флага синхронизации во временной папке + scope для гашения. */
    private suspend fun newFlagStore(initial: Boolean): Pair<SyncFlagDataStore, CoroutineScope> {
            val file = File(tmpFolder.newFolder(), "sync_settings.preferences_pb")
            val scope = CoroutineScope(Dispatchers.IO + Job())
            val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { file },
            )
            val store = SyncFlagDataStore(dataStore)
        store.setSyncEnabled(initial)
        return store to scope
    }

    // --- S17: физическое удаление карточки ---

    @Test
    fun deleteCard_flagOff_rowPhysicallyRemoved() = runTest {
        val deckId = newDeckId()
        insertDeck(deckId)
        val cardA = UUID.randomUUID().toString()
        val cardB = UUID.randomUUID().toString()
        insertCard(cardA, deckId, "a")
        insertCard(cardB, deckId, "b")
        assertEquals(2, database.cardDao().getCardsByDeck(deckId).size)

        // Флаг синхронизации не подключён (S14: значение по умолчанию false).
        cardRepository.deleteCard(cardA)

        assertNull(
            "S17: прямой DAO-запрос не находит карточку",
            database.cardDao().getCardByIdIncludingDeleted(cardA),
        )
        assertEquals(
            "S17: счётчик карточек колоды уменьшился",
            listOf(cardB),
            database.cardDao().getCardsByDeck(deckId).map { it.id },
        )
    }

    // --- S18: физическое удаление колоды каскадом ---

    @Test
    fun deleteDeck_flagOff_cascadeRemovesAllCards() = runTest {
        val deckId = newDeckId()
        insertDeck(deckId, "D")
        val cardIds = (1..5).map { index ->
            UUID.randomUUID().toString().also { insertCard(it, deckId, "card$index") }
        }
        assertEquals(5, database.cardDao().getCardsByDeck(deckId).size)

        deckRepository.deleteDeck(deckId)

        assertNull(
            "S18: строка колоды физически удалена",
            database.deckDao().getDeckByIdIncludingDeleted(deckId),
        )
        cardIds.forEach { cardId ->
            assertNull(
                "S18: карточка $cardId физически удалена каскадом",
                database.cardDao().getCardByIdIncludingDeleted(cardId),
            )
        }
        assertTrue(
            "S18: у колоды не осталось карточек",
            database.cardDao().getCardsByDeckIncludingDeleted(deckId).isEmpty(),
        )
    }

    // --- S16: ошибка чтения DataStore → физическое удаление ---

    @Test
    fun deleteCard_dataStoreReadError_deletesPhysically() = runTest {
        // GIVEN: повреждённый файл DataStore — чтение выбрасывает
        // CorruptionException, SyncFlagDataStore транслирует его в false.
        val dir = tmpFolder.newFolder()
        val file = File(dir, "sync_settings.preferences_pb")
        file.writeBytes(byteArrayOf(0x00, 0x01, 0x7F, 0x33, 0x33, 0x33))
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val dataStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val store = SyncFlagDataStore(dataStore)

        val flagValue = store.syncEnabled.first()
        assertFalse("S16: ошибка чтения DataStore → флаг трактуется как false", flagValue)

        val repositoryWithBrokenStore =
            RoomCardRepository(database.deckDao(), database.cardDao(), store)

        val deckId = newDeckId()
        insertDeck(deckId)
        val cardId = UUID.randomUUID().toString()
        insertCard(cardId, deckId)

        repositoryWithBrokenStore.deleteCard(cardId)

        assertNull(
            "S16: удаление выполнено физически, строки нет в БД",
            database.cardDao().getCardByIdIncludingDeleted(cardId),
        )

        scope.cancel()
    }

    // --- S19: мягкое удаление карточки ---

    @Test
    fun deleteCard_flagOn_rowSoftDeletedWithFlagsBumped() = runTest {
        val (store, scope) = newFlagStore(initial = true)
        val repository = RoomCardRepository(database.deckDao(), database.cardDao(), store)

        val deckId = newDeckId()
        insertDeck(deckId)
        val cardId = UUID.randomUUID().toString()
        // GIVEN: isChanged = true, revision = 2.
        database.cardDao().insertCard(
            CardEntity(
                id = cardId,
                deckId = deckId,
                front = "front",
                back = "back",
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

        repository.deleteCard(cardId)

        val row = database.cardDao().getCardByIdIncludingDeleted(cardId)
        assertNotNull("S19: строка осталась в БД", row)
        row!!
        assertTrue("S19: isDeleted = true", row.isDeleted)
        assertTrue("S19: isChanged = true", row.isChanged)
        assertEquals("S19: revision увеличился на 1", 3, row.revision)
        assertEquals("S19: данные карточки сохранены", "front", row.front)
        assertEquals("S19: данные карточки сохранены", "back", row.back)

        scope.cancel()
    }

    // --- S20: мягкое удаление колоды каскадно помечает карточки ---

    @Test
    fun deleteDeck_flagOn_deckAndCardsSoftDeleted() = runTest {
        val (store, scope) = newFlagStore(initial = true)
        val repository = RoomDeckRepository(database.deckDao(), store)

        val deckId = newDeckId()
        insertDeck(deckId, "D")
        val cardIds = (1..4).map { index ->
            UUID.randomUUID().toString().also { insertCard(it, deckId, "card$index") }
        }

        repository.deleteDeck(deckId)

        val deckRow = database.deckDao().getDeckByIdIncludingDeleted(deckId)
        assertNotNull("S20: строка колоды осталась в БД", deckRow)
        deckRow!!
        assertTrue("S20: колода isDeleted = true", deckRow.isDeleted)
        assertTrue("S20: колода isChanged = true", deckRow.isChanged)
        assertEquals("S20: revision колоды +1", 2, deckRow.revision)

        val cardsAfter = database.cardDao().getCardsByDeckIncludingDeleted(deckId)
        assertEquals("S20: все 4 карточки физически присутствуют в БД", 4, cardsAfter.size)
        cardIds.forEach { cardId ->
            val card = cardsAfter.first { it.id == cardId }
            assertTrue("S20: карточка isDeleted = true", card.isDeleted)
            assertTrue("S20: карточка isChanged = true", card.isChanged)
            assertEquals("S20: revision карточки +1", 2, card.revision)
        }
        assertEquals(
            "S20: колода исчезла из списка",
            emptyList<String>(),
            database.deckDao().observeDecksWithStats(1_000).first().map { it.deckId },
        )

        scope.cancel()
    }

    // --- S23: мягко удалённая запись доступна по прямому запросу к DAO ---

    @Test
    fun softDeletedCard_directDaoQueryWithoutFilter_findsRow() = runTest {
        val (store, scope) = newFlagStore(initial = true)
        val repository = RoomCardRepository(database.deckDao(), database.cardDao(), store)

        val deckId = newDeckId()
        insertDeck(deckId)
        val cardId = UUID.randomUUID().toString()
        insertCard(cardId, deckId, "front")

        repository.deleteCard(cardId)

        val row = database.cardDao().getCardByIdIncludingDeleted(cardId)
        assertNotNull("S23: прямой DAO-запрос без фильтра находит запись", row)
        row!!
        assertTrue("S23: isDeleted = true у найденной записи", row.isDeleted)
        assertEquals("S23: исходные данные сохранены", "front", row.front)

        scope.cancel()
    }

    // --- S24: переключение флага не трогает мягко удалённые записи ---

    @Test
    fun flagToggledOff_softDeletedRowsUntouched() = runTest {
        val (storeA, scopeA) = newFlagStore(initial = true)
        val softRepository = RoomCardRepository(database.deckDao(), database.cardDao(), storeA)

        val deckId = newDeckId()
        insertDeck(deckId)
        val cardId = UUID.randomUUID().toString()
        insertCard(cardId, deckId, "front", revision = 7)

        softRepository.deleteCard(cardId)
        scopeA.cancel()

        // WHEN: флаг переключается на выключенный напрямую в DataStore,
        // приложение «перезапускается» (новые экземпляры хранилища и репозитория).
        val (storeB, scopeB) = newFlagStore(initial = false)
        assertFalse("S24: флаг теперь выключен", storeB.syncEnabled.first())

        // THEN: мягко удалённые записи не изменяются и не восстанавливаются:
        // никаких операций над ними переключение флага не запускает.
        val row = database.cardDao().getCardByIdIncludingDeleted(cardId)
        assertNotNull("S24: строка осталась в БД", row)
        row!!
        assertTrue("S24: isDeleted по-прежнему true", row.isDeleted)
        assertEquals("S24: revision не изменился", 8, row.revision)

        scopeB.cancel()
    }
}
