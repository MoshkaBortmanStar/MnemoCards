package com.mnemocards.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.db.sync.LastSyncResultEntity
import com.mnemocards.data.network.SyncApi
import com.mnemocards.data.syncsettings.TokenPair
import com.mnemocards.data.syncsettings.TokenStorage
import java.util.UUID
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
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
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Тесты обработки ошибок сессии (change-009, T11/T12): сеть без ответа
 * (S15), 5xx (S16), 400 (S17), 404 на потоке карточек (S18), повреждённое
 * тело (S19), 413 (S26). Ошибка не рушит БД и уже применённые потоки.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncEngineErrorTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var server: MockWebServer
    private lateinit var tokenStorage: FakeTokenStorage
    private lateinit var engine: SyncEngine

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder()
            .client(OkHttpClient())
            .baseUrl(server.url("/"))
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(SyncApi::class.java)
        tokenStorage = FakeTokenStorage()
        engine = SyncEngine(db, api, tokenStorage) { server.url("/").toString() }
        tokenStorage.saved.add(TokenPair("access-1", "refresh-1"))
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    private class FakeTokenStorage : TokenStorage {
        val saved = mutableListOf<TokenPair>()
        override suspend fun saveTokens(tokens: TokenPair) {
            saved.add(tokens)
        }

        override suspend fun tokens(): TokenPair? = saved.lastOrNull()
        override suspend fun clear() {
            saved.clear()
        }
    }

    private fun uuid() = UUID.randomUUID().toString()

    private fun okDecks(revision: Long) =
        MockResponse().setBody("""{"revision":$revision,"fullSync":false,"decks":[]}""")

    private fun okCards(revision: Long) =
        MockResponse().setBody("""{"revision":$revision,"fullSync":false,"cards":[]}""")

    private suspend fun insertDeck(isChanged: Boolean = true): DeckEntity {
        val entity = DeckEntity(uuid(), "Колода", isChanged, if (isChanged) 1 else 0, false)
        db.deckDao().insertDeck(entity)
        return entity
    }

    private suspend fun insertPendingCard(deckUuid: String): CardEntity {
        val entity = CardEntity(
            id = uuid(),
            deckId = deckUuid,
            front = "слово",
            back = "перевод",
            createdAt = 0,
            isChanged = true,
            revision = 1,
        )
        db.cardDao().insertCard(entity)
        return entity
    }

    @Test
    fun `connection failure aborts session without touching data`() = runTest {
        val deck = insertDeck()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))

        val error = engine.syncNow()

        assertTrue(error is SyncError.Network) // S15
        val local = db.deckDao().getDeckByIdIncludingDeleted(deck.id)!!
        assertTrue(local.isChanged)
        assertEquals(0L, db.syncStateDao().getRevision(SyncApplier.STREAM_DECKS))
    }

    @Test
    fun `server error on cards stream keeps applied decks stream`() = runTest {
        val deck = insertDeck()
        insertPendingCard(deck.id)
        server.enqueue(okDecks(10))
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

        val error = engine.syncNow()

        assertTrue(error is SyncError.Server) // S16
        assertEquals(10L, db.syncStateDao().getRevision(SyncApplier.STREAM_DECKS))
        // Ревизия упавшего потока карточек не изменилась.
        assertEquals(0L, db.syncStateDao().getRevision(SyncApplier.cardsStream(deck.id)))
    }

    @Test
    fun `bad request leaves body unapplied pending kept and revision unchanged`() = runTest {
        val deck = insertDeck()
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":{"code":"VALIDATION_FAILED","message":"имя"}}"""),
        )

        val error = engine.syncNow()

        assertTrue(error is SyncError.Validation) // S17
        val local = db.deckDao().getDeckByIdIncludingDeleted(deck.id)!!
        assertTrue(local.isChanged)
        assertEquals(0L, db.syncStateDao().getRevision(SyncApplier.STREAM_DECKS))
    }

    @Test
    fun `not found on cards stream deletes deck locally and session continues`() = runTest {
        val deck = insertDeck()
        val card = insertPendingCard(deck.id)
        val other = insertDeck()
        insertPendingCard(other.id)
        server.enqueue(okDecks(10))
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}")) // колода 1
        server.enqueue(okCards(30)) // колода 2

        val error = engine.syncNow()

        assertNull(error) // S18: сессия продолжается
        assertNull(db.deckDao().getDeckByIdIncludingDeleted(deck.id))
        assertNull(db.cardDao().getCardByIdIncludingDeleted(card.id))
        assertEquals(30L, db.syncStateDao().getRevision(SyncApplier.cardsStream(other.id)))
    }

    @Test
    fun `malformed success body is format error and database intact`() = runTest {
        insertDeck()
        server.enqueue(MockResponse().setBody("""{"revision":"oops","items":[]}"""))

        val error = engine.syncNow()

        assertTrue(error is SyncError.Format) // S19
    }

    @Test
    fun `payload too large aborts session keeping pending`() = runTest {
        insertDeck()
        server.enqueue(MockResponse().setResponseCode(413).setBody("{}"))

        val error = engine.syncNow()

        assertTrue(error is SyncError.TooManyChanges) // S26
        assertEquals(1, db.deckDao().getPendingDecks().size)
    }

    @Test
    fun `failed session records error result with message`() = runTest {
        insertDeck()
        server.enqueue(MockResponse().setResponseCode(500).setBody("{}"))

        engine.syncNow()

        val last = db.syncStateDao().getLastSyncResult()
        assertNotNull(last) // S2: время, статус, текст ошибки
        assertEquals(LastSyncResultEntity.STATUS_ERROR, last!!.status)
        assertNotNull(last.errorMessage)
    }
}
