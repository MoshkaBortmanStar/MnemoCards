package com.mnemocards.data.network

import com.mnemocards.data.network.dto.models.LoginRequest
import com.mnemocards.data.network.dto.models.TokenPair
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
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
 */
interface SyncApi {

    /** GET /api/v1/health — проверка доступности сервера (S5–S9). */
    @GET
    suspend fun health(@Url url: String): Response<Unit>

    /** POST /api/v1/auth/login — вход, выдача пары токенов (S10–S14). */
    @POST
    suspend fun login(@Url url: String, @Body body: LoginRequest): Response<TokenPair>
}
