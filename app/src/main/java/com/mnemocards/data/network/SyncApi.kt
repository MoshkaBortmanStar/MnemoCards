package com.mnemocards.data.network

import com.mnemocards.data.network.dto.models.LoginRequest
import com.mnemocards.data.network.dto.models.RefreshRequest
import com.mnemocards.data.network.dto.models.SyncCardsRequest
import com.mnemocards.data.network.dto.models.SyncCardsResponse
import com.mnemocards.data.network.dto.models.SyncDecksRequest
import com.mnemocards.data.network.dto.models.SyncDecksResponse
import com.mnemocards.data.network.dto.models.TokenPair
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Сетевой интерфейс сервера синхронизации (specs/API.md, specs/openapi.yaml).
 * Тела запросов и ответов — DTO, сгенерированные из openapi.yaml
 * (S22): ручные сетевые модели не заводятся.
 *
 * Базовый адрес подставляется динамически из поля «Адрес сервера» через
 * параметр `@Url` — Retrofit-инстанс создаётся с технической базой,
 * реальный сервер указывается при каждом вызове.
 *
 * change-009: потоковая синхронизация колод и карточек с ревизиями
 * (S1, S3–S5) и обновление пары токенов (S12).
 */
interface SyncApi {

    /** GET /api/v1/health — проверка доступности сервера (S5–S9). */
    @GET
    suspend fun health(@Url url: String): Response<Unit>

    /** POST /api/v1/auth/login — вход, выдача пары токенов (S10–S14). */
    @POST
    suspend fun login(@Url url: String, @Body body: LoginRequest): Response<TokenPair>

    /** POST /api/v1/auth/refresh — обновление пары токенов (S12). */
    @POST
    suspend fun refresh(@Url url: String, @Body body: RefreshRequest): Response<TokenPair>

    /**
     * POST /api/v1/sync/decks — обмен изменениями колод с ревизии
     * [SyncDecksRequest.since] (S1, S4, S5).
     */
    @POST
    suspend fun syncDecks(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: SyncDecksRequest,
    ): Response<SyncDecksResponse>

    /**
     * POST /api/v1/sync/decks/{deckUuid}/cards — обмен изменениями
     * карточек колоды (S3); полный путь с UUID колоды передаётся
     * в [url] вызывающим кодом.
     */
    @POST
    suspend fun syncCards(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Body body: SyncCardsRequest,
    ): Response<SyncCardsResponse>
}
