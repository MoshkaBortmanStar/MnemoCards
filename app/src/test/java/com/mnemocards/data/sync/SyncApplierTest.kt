package com.mnemocards.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.network.dto.models.CardRecord
import com.mnemocards.data.network.dto.models.DeckRecord
import java.util.UUID
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты применения ответов сервера к Room (change-009, T7/T8):
 * перезапись (S6), удалённые на сервере (S7), полная замена потока (S8),
 * подтверждение по снапшоту (S9–S11), идемпотентность (S24),
 * защита правки в полёте (S25).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncApplierTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var applier: SyncApplier

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        applier = SyncApplier(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun uuid() = UUID.randomUUID().toString()

    private fun deckRecord(
        uuid: String,
        name: String = "Колода",
        deleted: Boolean = false,
    ) = DeckRecord(
        uuid = uuid,
        name = name,
        updatedAt = 1_000,
        deletedAt = if (deleted) 2_000 else null,
    )

    private fun cardRecord(
        uuid: String,
        deckUuid: String,
        front: String = "слово",
        deleted: Boolean = false,
    ) = CardRecord(
        uuid = uuid,
        deckUuid = deckUuid,
        front = front,
        back = "перевод",
        easeFactor = 2.5,
        repetition = 1,
        intervalDays = 2,
        dueAt = 3_000,
        updatedAt = 1_000,
        deletedAt = if (deleted) 2_000 else null,
    )

    private suspend fun insertLocalDeck(
        uuid: String = uuid(),
        name: String = "Локальная",
        isChanged: Boolean = false,
        revision: Int = 0,
        isDeleted: Boolean = false,
    ): DeckEntity {
        val entity = DeckEntity(uuid, name, isChanged, revision, isDeleted)
        db.deckDao().insertDeck(entity)
        return entity
    }

    private suspend fun insertLocalCard(
        deckUuid: String,
        uuid: String = uuid(),
        front: String = "локально",
        isChanged: Boolean = false,
        revision: Int = 0,
    ): CardEntity {
        val entity = CardEntity(
            id = uuid,
            deckId = deckUuid,
            front = front,
            back = "перевод",
            createdAt = 0,
            isChanged = isChanged,
            revision = revision,
        )
        db.cardDao().insertCard(entity)
        return entity
    }

    // --- Поток колод ---

    @Test
    fun `server record overwrites unchanged local copy`() = runTest {
        val local = insertLocalDeck(name = "Старое имя")
        applier.applyDecksResponse(listOf(deckRecord(local.id, name = "Новое имя")), false, 10)

        val after = db.deckDao().getDeckByIdIncludingDeleted(local.id)!!
        assertEquals("Новое имя", after.name) // S6
        assertEquals(false, after.isChanged)
        assertEquals(10L, db.syncStateDao().getRevision(SyncApplier.STREAM_DECKS))
    }

    @Test
    fun `server record inserts when no local copy`() = runTest {
        val uuid = uuid()
        applier.applyDecksResponse(listOf(deckRecord(uuid)), false, 1)

        val inserted = db.deckDao().getDeckByIdIncludingDeleted(uuid)
        assertTrue(inserted != null && !inserted.isChanged) // S4, S6
    }

    @Test
    fun `record deleted on server is removed physically without tombstone`() = runTest {
        val local = insertLocalDeck()
        applier.applyDecksResponse(listOf(deckRecord(local.id, deleted = true)), false, 10)

        assertNull(db.deckDao().getDeckByIdIncludingDeleted(local.id)) // S7
    }

    @Test
    fun `full sync replaces stream dropping locals absent from response`() = runTest {
        val kept = insertLocalDeck()
        insertLocalDeck() // вне ответа
        applier.applyDecksResponse(listOf(deckRecord(kept.id)), fullSync = true, revision = 5)

        val all = db.deckDao().getDecksIncludingDeleted()
        assertEquals(listOf(kept.id), all.map { it.id }) // S8
    }

    // Регрессия: подключение к ДРУГОМУ серверу стирало локальные колоды.
    // Клиент просил since из чужой нумерации, новый сервер отвечал
    // fullSync с ревизией 0 и пустым срезом — и колоды удалялись как
    // «отсутствующие на сервере». Ревизия не может уменьшаться у сервера,
    // который помнит нашу историю, поэтому такой ответ не авторитетен.
    @Test
    fun `full sync keeps locals when server revision went backwards`() = runTest {
        val first = insertLocalDeck()
        val second = insertLocalDeck()

        applier.applyDecksResponse(
            records = emptyList(),
            fullSync = true,
            revision = 0,
            since = 7,
        )

        val all = db.deckDao().getDecksIncludingDeleted().map { it.id }.toSet()
        assertEquals(
            "Пустой срез сервера с меньшей ревизией не доказывает удаление",
            setOf(first.id, second.id),
            all,
        )
    }

    @Test
    fun `full sync keeps cards when server revision went backwards`() = runTest {
        val deck = insertLocalDeck()
        val card = insertLocalCard(deck.id)

        applier.applyCardsResponse(
            deckId = deck.id,
            records = emptyList(),
            fullSync = true,
            revision = 0,
            since = 4,
        )

        val all = db.cardDao().getCardsByDeckIncludingDeleted(deck.id).map { it.id }
        assertEquals(listOf(card.id), all)
    }

    // Обратная сторона страховки: при нормальном росте ревизии полная
    // замена потока должна работать как прежде (S8).
    @Test
    fun `full sync still drops locals when server revision moved forward`() = runTest {
        val kept = insertLocalDeck()
        insertLocalDeck() // вне ответа

        applier.applyDecksResponse(
            records = listOf(deckRecord(kept.id)),
            fullSync = true,
            revision = 9,
            since = 5,
        )

        val all = db.deckDao().getDecksIncludingDeleted()
        assertEquals(listOf(kept.id), all.map { it.id })
    }

    @Test
    fun `confirmed tombstone is deleted and pending cleared on revision match`() = runTest {
        val tombstone = insertLocalDeck(isChanged = true, revision = 3, isDeleted = true)
        val sent = insertLocalDeck(isChanged = true, revision = 2)
        applier.applyDecksResponse(
            emptyList(),
            fullSync = false,
            revision = 10,
            snapshot = mapOf(tombstone.id to 3, sent.id to 2),
        )

        assertNull(db.deckDao().getDeckByIdIncludingDeleted(tombstone.id)) // S11
        assertEquals(false, db.deckDao().getDeckByIdIncludingDeleted(sent.id)!!.isChanged) // S9
    }

    @Test
    fun `record edited after snapshot keeps local pending state`() = runTest {
        val local = insertLocalDeck(isChanged = true, revision = 2)
        // Снапшот отправки устарел: локальная правка ушла вперёд (S10, S25).
        applier.applyDecksResponse(
            listOf(deckRecord(local.id, name = "Сервер")),
            fullSync = false,
            revision = 10,
            snapshot = mapOf(local.id to 1),
        )

        val after = db.deckDao().getDeckByIdIncludingDeleted(local.id)!!
        assertEquals("Локальная", after.name) // серверная версия не применена
        assertTrue(after.isChanged)
    }

    @Test
    fun `applying same response twice creates no duplicates`() = runTest {
        val uuid = uuid()
        val records = listOf(deckRecord(uuid))
        applier.applyDecksResponse(records, false, 1) // S24
        applier.applyDecksResponse(records, false, 1)

        assertEquals(1, db.deckDao().getDecksIncludingDeleted().size)
    }

    // --- Поток карточек ---

    @Test
    fun `card stream applies overwrite delete and full replacement`() = runTest {
        val deckUuid = uuid()
        db.deckDao().insertDeck(DeckEntity(deckUuid, "Колода", false, 0, false))
        val kept = insertLocalCard(deckUuid, front = "старое")
        insertLocalCard(deckUuid) // вне полного среза — будет удалена (S8)
        val deletedOnServer = insertLocalCard(deckUuid)

        // S6: перезапись; S7: серверное удаление — физически.
        applier.applyCardsResponse(
            deckId = deckUuid,
            records = listOf(
                cardRecord(kept.id, deckUuid, front = "новое"),
                cardRecord(deletedOnServer.id, deckUuid, deleted = true),
            ),
            fullSync = false,
            revision = 3,
        )
        assertEquals("новое", db.cardDao().getCardByIdIncludingDeleted(kept.id)!!.front)
        assertNull(db.cardDao().getCardByIdIncludingDeleted(deletedOnServer.id))

        // S8: полный срез — записи вне ответа удаляются.
        applier.applyCardsResponse(deckUuid, listOf(cardRecord(kept.id, deckUuid)), true, 4)
        assertEquals(listOf(kept.id), db.cardDao().getCardsByDeckIncludingDeleted(deckUuid).map { it.id })
        assertEquals(4L, db.syncStateDao().getRevision(SyncApplier.cardsStream(deckUuid)))
    }

    @Test
    fun `card record maps all scheduling fields`() = runTest {
        val deckUuid = uuid()
        db.deckDao().insertDeck(DeckEntity(deckUuid, "Колода", false, 0, false))
        val cardUuid = uuid()

        applier.applyCardsResponse(
            deckUuid,
            listOf(cardRecord(cardUuid, deckUuid)),
            fullSync = false,
            revision = 1,
        )

        val card = db.cardDao().getCardByIdIncludingDeleted(cardUuid)!!
        assertEquals("перевод", card.back)
        assertEquals(2.5, card.easinessFactor, 0.001)
        assertEquals(1, card.repetitionNumber)
        assertEquals(2, card.intervalDays)
        assertEquals(3_000L, card.dueDate)
        assertEquals(false, card.isChanged)
    }
}
