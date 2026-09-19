package com.mnemocards.data.network

import com.mnemocards.data.network.dto.models.CardChange
import com.mnemocards.data.network.dto.models.DeckChange
import com.mnemocards.data.network.dto.models.RefreshRequest
import com.mnemocards.data.network.dto.models.SyncCardsRequest
import com.mnemocards.data.network.dto.models.SyncDecksRequest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Тесты сетевого слоя синхронизации (change-009, T5/T6): методы, пути,
 * заголовок авторизации и сериализация тел — строго по контрактам
 * specs/openapi.yaml (S1, S3, S4, S12).
 */
class SyncApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: SyncApi

    @Before
    fun setUp() {
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
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `sync decks posts to stream path with since and changes`() = runTest {
        server.enqueue(
            MockResponse().setBody("""{"revision":42,"fullSync":false,"decks":[]}"""),
        )

        val response = api.syncDecks(
            "${server.url("/")}api/v1/sync/decks",
            "Bearer token-1",
            SyncDecksRequest(
                since = 41,
                changes = listOf(DeckChange(uuid = "u-1", name = "Колода", deleted = false)),
            ),
        )

        assertTrue(response.isSuccessful)
        assertEquals(42L, response.body()!!.revision)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/sync/decks", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("Bearer token-1", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"since\":41"))
        assertTrue(body.contains("\"uuid\":\"u-1\""))
    }

    @Test
    fun `sync cards posts to deck stream path with deck uuid`() = runTest {
        val deckUuid = "11111111-2222-3333-4444-555555555555"
        server.enqueue(
            MockResponse().setBody("""{"revision":7,"fullSync":false,"cards":[]}"""),
        )

        val response = api.syncCards(
            "${server.url("/")}api/v1/sync/decks/$deckUuid/cards",
            "Bearer token-1",
            SyncCardsRequest(
                since = 0,
                changes = listOf(
                    CardChange(
                        uuid = "c-1",
                        deckUuid = deckUuid,
                        front = "слово",
                        back = "перевод",
                        easeFactor = 2.5,
                        repetition = 0,
                        intervalDays = 0,
                        dueAt = 0,
                        deleted = false,
                    ),
                ),
            ),
        )

        assertTrue(response.isSuccessful)
        assertEquals(7L, response.body()!!.revision)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/sync/decks/$deckUuid/cards", recorded.path)
        assertEquals("POST", recorded.method)
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"front\":\"слово\""))
        assertTrue(body.contains("\"easeFactor\":2.5"))
    }

    @Test
    fun `refresh posts refresh token and parses new pair`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"accessToken":"a2","accessTokenExpiresAt":10,
                 "refreshToken":"r2","refreshTokenExpiresAt":20}
                """.trimIndent(),
            ),
        )

        val response = api.refresh(
            "${server.url("/")}api/v1/auth/refresh",
            RefreshRequest(refreshToken = "r1"),
        )

        assertTrue(response.isSuccessful)
        assertEquals("a2", response.body()!!.accessToken)
        assertEquals("r2", response.body()!!.refreshToken)

        val recorded = server.takeRequest()
        assertEquals("/api/v1/auth/refresh", recorded.path)
        assertTrue(recorded.body.readUtf8().contains("\"refreshToken\":\"r1\""))
    }
}
