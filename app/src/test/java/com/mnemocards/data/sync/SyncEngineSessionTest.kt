package com.mnemocards.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.db.sync.SyncStateEntity
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
 * Интеграционные тесты сессии движка (change-009, T9/T10): порядок
 * «колоды → карточки живых колод» (S1, S3), since=0 для нового потока
 * (S4), отправка только pending включая tombstone (S5), возобновление
 * по ревизиям (S24), чанки ≤1000 и ревизия из последнего ответа (S26),
 * прозрачный refresh (S12) и отказ refresh (S13).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncEngineSessionTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var server: MockWebServer
    private lateinit var api: SyncApi
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
        api = Retrofit.Builder()
            .client(OkHttpClient())
            .baseUrl(server.url("/"))
            .addConverterFactory(
                Json { ignoreUnknownKeys = true }
                    .asConverterFactory("application/json".toMediaType()),
            )
            .build()
            .create(SyncApi::class.java)
        tokenStorage = FakeTokenStorage()
        engine = SyncEngine(
            db = db,
            api = api,
            tokenStorage = tokenStorage,
            serverAddress = { server.url("/").toString() },
        )
        tokenStorage.saved.add(TokenPair("access-1", "refresh-1"))
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
    }

    /** Простое хранилище токенов в памяти. */
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

    private fun okDecks(revision: Long, decksJson: String = "[]", fullSync: Boolean = false) =
        MockResponse().setBody(
            """{"revision":$revision,"fullSync":$fullSync,"decks":$decksJson}""",
        )

    private fun okCards(revision: Long, cardsJson: String = "[]", fullSync: Boolean = false) =
        MockResponse().setBody(
            """{"revision":$revision,"fullSync":$fullSync,"cards":$cardsJson}""",
        )

    private suspend fun insertDeck(
        uuid: String = uuid(),
        name: String = "Колода",
        isChanged: Boolean = false,
        revision: Int = 0,
        isDeleted: Boolean = false,
    ): DeckEntity {
        val entity = DeckEntity(uuid, name, isChanged, revision, isDeleted)
        db.deckDao().insertDeck(entity)
        return entity
    }

    private suspend fun insertPendingCard(deckUuid: String, front: String = "слово"): CardEntity {
        val entity = CardEntity(
            id = uuid(),
            deckId = deckUuid,
            front = front,
            back = "перевод",
            createdAt = 0,
            isChanged = true,
            revision = 1,
        )
        db.cardDao().insertCard(entity)
        return entity
    }

    @Test
    fun `session syncs decks stream first then cards of alive decks`() = runTest {
        val deck = insertDeck(isChanged = true, revision = 1)
        val card = insertPendingCard(deck.id)
        server.enqueue(okDecks(10))
        server.enqueue(okCards(20))

        val error = engine.syncNow()

        assertNull(error)
        // S1, S3: порядок запросов — сначала колоды, затем карточки живой колоды.
        assertEquals("/api/v1/sync/decks", server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!.path)
        assertEquals("/api/v1/sync/decks/${deck.id}/cards", server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!.path)
        assertEquals(10L, db.syncStateDao().getRevision(SyncApplier.STREAM_DECKS))
        assertEquals(20L, db.syncStateDao().getRevision(SyncApplier.cardsStream(deck.id)))
        // S9: подтверждённые отправки теряют признак локального изменения.
        assertEquals(false, db.deckDao().getDeckByIdIncludingDeleted(deck.id)!!.isChanged)
        assertEquals(false, db.cardDao().getCardByIdIncludingDeleted(card.id)!!.isChanged)
    }

    @Test
    fun `only pending records are sent including tombstones`() = runTest {
        val pending = insertDeck(isChanged = true, revision = 2)
        val tombstone = insertDeck(isChanged = true, revision = 3, isDeleted = true)
        insertDeck() // подтверждённая ранее — не отправляется
        // Живая колода после применения потока колод: карточный поток пуст.
        server.enqueue(okDecks(10))
        server.enqueue(okCards(0))

        engine.syncNow()

        val body = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8()
        assertTrue(body.contains(pending.id)) // S5
        assertTrue(body.contains(tombstone.id))
        assertEquals(2, Regex("\"uuid\"").findAll(body).count())
        assertTrue(body.contains("\"deleted\":true"))
    }

    @Test
    fun `new deck stream starts with since zero`() = runTest {
        insertDeck(isChanged = true, revision = 1)
        server.enqueue(okDecks(1))
        server.enqueue(okCards(0))

        engine.syncNow()

        val body = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8()
        assertTrue(body.contains("\"since\":0")) // S4
    }

    @Test
    fun `session resumes with saved stream revisions`() = runTest {
        val deck = insertDeck()
        // S24: предыдущая сессия оборвалась — ревизии уже сохранены.
        db.syncStateDao().saveRevision(SyncStateEntity(SyncApplier.STREAM_DECKS, 42))
        db.syncStateDao().saveRevision(SyncStateEntity(SyncApplier.cardsStream(deck.id), 7))
        server.enqueue(okDecks(42))
        server.enqueue(okCards(7))

        engine.syncNow()

        assertTrue(server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8().contains("\"since\":42"))
        assertTrue(server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8().contains("\"since\":7"))
    }

    @Test
    fun `more than thousand changes are chunked and revision comes from last response`() = runTest {
        // S26: 1200 pending-карточек одной колоды — чанки 1000 + 200,
        // ревизия потока — из последнего ответа.
        val deck = insertDeck()
        repeat(1200) {
            insertPendingCard(deck.id)
        }
        server.enqueue(okDecks(0))
        server.enqueue(okCards(100))
        server.enqueue(okCards(200))

        val error = engine.syncNow()

        assertNull(error)
        server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!! // поток колод
        val first = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8()
        val second = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!.body.readUtf8()
        assertEquals(1000, Regex("\"uuid\"").findAll(first).count())
        assertEquals(200, Regex("\"uuid\"").findAll(second).count())
        assertEquals(200L, db.syncStateDao().getRevision(SyncApplier.cardsStream(deck.id)))
    }

    @Test
    fun `unauthorized sync triggers refresh and retry with new token`() = runTest {
        val deck = insertDeck(isChanged = true, revision = 1)
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.enqueue(
            MockResponse().setBody(
                """{"accessToken":"a2","accessTokenExpiresAt":9,
                    "refreshToken":"r2","refreshTokenExpiresAt":9}""",
            ),
        )
        server.enqueue(okDecks(5))
        server.enqueue(okCards(0))

        val error = engine.syncNow()

        assertNull(error)
        assertEquals("/api/v1/sync/decks", server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!.path) // 401
        assertEquals("/api/v1/auth/refresh", server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!.path) // S12
        val retried = server.takeRequest(5, java.util.concurrent.TimeUnit.SECONDS)!!
        assertEquals("/api/v1/sync/decks", retried.path)
        assertEquals("Bearer a2", retried.getHeader("Authorization"))
        assertEquals(TokenPair("a2", "r2"), tokenStorage.saved.last())
        assertEquals(5L, db.syncStateDao().getRevision(SyncApplier.STREAM_DECKS))
    }

    @Test
    fun `rejected refresh stops session and keeps tokens data and revisions`() = runTest {
        val deck = insertDeck(isChanged = true, revision = 1)
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))

        val error = engine.syncNow()

        assertTrue(error is SyncError.RequiresLogin) // S13
        assertEquals(2, server.requestCount) // повторного запроса sync не было
        assertEquals(TokenPair("access-1", "refresh-1"), tokenStorage.saved.last())
        val local = db.deckDao().getDeckByIdIncludingDeleted(deck.id)!!
        assertTrue(local.isChanged) // данные и признаки не тронуты
        assertEquals(0L, db.syncStateDao().getRevision(SyncApplier.STREAM_DECKS))
    }
}
