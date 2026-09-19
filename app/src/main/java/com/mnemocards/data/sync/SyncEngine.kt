package com.mnemocards.data.sync

import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.db.sync.LastSyncResultEntity
import com.mnemocards.data.network.SyncApi
import com.mnemocards.data.network.dto.models.CardChange
import com.mnemocards.data.network.dto.models.DeckChange
import com.mnemocards.data.network.dto.models.RefreshRequest
import com.mnemocards.data.network.dto.models.SyncCardsRequest
import com.mnemocards.data.network.dto.models.SyncDecksRequest
import com.mnemocards.data.syncsettings.TokenPair
import com.mnemocards.data.syncsettings.TokenStorage
import java.io.IOException
import kotlinx.serialization.SerializationException
import retrofit2.Response

/**
 * Движок фоновой сессии синхронизации (change-009, T10/T12).
 *
 * Последовательность: сначала поток колод (`POST /api/v1/sync/decks`),
 * затем карточки каждой живой колоды
 * (`POST /api/v1/sync/decks/{deckUuid}/cards`) с её сохранённой ревизией
 * (S1, S3). В отправку попадают только записи с признаком локального
 * изменения, включая tombstone (S5); изменения уходят чанками ≤1000,
 * снапшоты счётчиков правок (revision) фиксируются в момент отправки
 * (S9, S26).
 *
 * 401 на синхронизации — прозрачный refresh и повтор запроса; отказ
 * refresh — состояние «требуется вход» (S12, S13). 404 на потоке
 * карточек — колода удалена на сервере, локально удаляется полностью,
 * сессия продолжается (S18).
 */
class SyncEngine(
    private val db: MnemoCardsDatabase,
    private val api: SyncApi,
    private val tokenStorage: TokenStorage,
    /** Адрес сервера из настроек (change-008); null — не настроено. */
    private val serverAddress: suspend () -> String?,
) {

    private val applier = SyncApplier(db)

    /** Лимит specs/API.md: не более 1000 записей в одном запросе (S26). */
    private val chunkLimit = 1000

    /**
     * Выполняет сессию; возвращает null при успехе либо ошибку [SyncError].
     * Итог (время, статус, текст ошибки) фиксируется в LastSyncResultEntity (S2).
     */
    suspend fun syncNow(): SyncError? = try {
        runSession()
        record(null)
        null
    } catch (abort: SyncAbort) {
        record(abort.error)
        abort.error
    }

    /** Внутреннее прерывание сессии: уже применённые потоки сохранены (S16). */
    private class SyncAbort(val error: SyncError) : Exception(error.message)

    private suspend fun record(error: SyncError?) {
        db.syncStateDao().saveLastSyncResult(
            LastSyncResultEntity(
                finishedAtMillis = System.currentTimeMillis(),
                status = when (error) {
                    null -> LastSyncResultEntity.STATUS_SUCCESS
                    is SyncError.RequiresLogin -> LastSyncResultEntity.STATUS_REQUIRES_LOGIN
                    else -> LastSyncResultEntity.STATUS_ERROR
                },
                errorMessage = error?.message,
            ),
        )
    }

    // --- Сессия ---

    private suspend fun runSession() {
        val baseUrl = serverAddress()?.let(::normalize)
            ?: throw SyncAbort(SyncError.NotConfigured)
        syncDecksStream(baseUrl)
        // S3: карточки — только живых колод, после применения списка колод.
        for (deckId in db.deckDao().getAliveDeckIds()) {
            syncCardsStream(baseUrl, deckId)
        }
    }

    /** Как в SyncSettingsRepository (change-008): без хвостового слэша. */
    private fun normalize(address: String): String = address.trim().trimEnd('/')

    /**
     * Поток колод: чанки изменённых колод, применение каждого ответа
     * (S5, S26). Поток опрашивается всегда — даже без локальных
     * изменений: сервер может прислать свои (S4, S24).
     */
    private suspend fun syncDecksStream(baseUrl: String) {
        val pending = db.deckDao().getPendingDecks()
        val chunks = pending.sortedBy { it.id }.chunked(chunkLimit)
        val batches = chunks.ifEmpty { listOf(emptyList()) }
        batches.forEach { chunk ->
            // S9: снапшот счётчиков в момент отправки.
            val snapshot = chunk.associate { it.id to it.revision }
            val since = db.syncStateDao().getRevision(SyncApplier.STREAM_DECKS)
            val response = authorized(baseUrl) { url, auth ->
                api.syncDecks(
                    "$url${PATH_SYNC_DECKS}",
                    auth,
                    SyncDecksRequest(
                        since = since,
                        changes = chunk.map { it.toDeckChange() },
                    ),
                )
            }.ensureSuccess(SyncApplier.STREAM_DECKS)
            applier.applyDecksResponse(
                records = response.decks,
                fullSync = response.fullSync,
                revision = response.revision,
                snapshot = snapshot,
            )
        }
    }

    /**
     * Поток карточек колоды со своей ревизией (S4, S24); 404 — колода
     * удалена на сервере: локальное удаление колоды и карточек целиком,
     * сессия продолжается (S18).
     */
    private suspend fun syncCardsStream(baseUrl: String, deckId: String) {
        val streamKey = SyncApplier.cardsStream(deckId)
        try {
            val pending = db.cardDao().getPendingCards(deckId)
            val chunks = pending.sortedBy { it.id }.chunked(chunkLimit)
            val batches = chunks.ifEmpty { listOf(emptyList()) }
            batches.forEach { chunk ->
                val snapshot = chunk.associate { it.id to it.revision }
                val since = db.syncStateDao().getRevision(streamKey)
                val response = authorized(baseUrl) { url, auth ->
                    api.syncCards(
                        "$url${PATH_SYNC_CARDS_PREFIX}$deckId${PATH_SYNC_CARDS_SUFFIX}",
                        auth,
                        SyncCardsRequest(
                            since = since,
                            changes = chunk.map { it.toCardChange() },
                        ),
                    )
                }.ensureSuccess(streamKey)
                applier.applyCardsResponse(
                    deckId = deckId,
                    records = response.cards,
                    fullSync = response.fullSync,
                    revision = response.revision,
                    snapshot = snapshot,
                )
            }
        } catch (abort: SyncAbort) {
            val error = abort.error
            if (error is SyncError.StreamDeleted) {
                // S18: поток удалён — полное локальное удаление колоды.
                db.deckDao().deleteDeckWithCards(deckId)
            } else {
                throw abort
            }
        }
    }

    // --- Авторизация: прозрачный refresh (S12, S13) ---

    /**
     * Выполняет вызов с текущим access-токеном; на 401 — один refresh и
     * повтор. Отказ refresh — «требуется вход», бесконечных циклов нет.
     */
    private suspend fun <T> authorized(
        baseUrl: String,
        call: suspend (url: String, authorization: String) -> Response<T>,
    ): Response<T> {
        val tokens = tokenStorage.tokens()
            ?: throw SyncAbort(SyncError.RequiresLogin)
        var response = callNetwork { call(baseUrl, "Bearer ${tokens.accessToken}") }
        if (response.code() == HTTP_UNAUTHORIZED) {
            val refreshed = refreshTokens(baseUrl, tokens.refreshToken)
                ?: throw SyncAbort(SyncError.RequiresLogin) // S13
            response = callNetwork { call(baseUrl, "Bearer ${refreshed.accessToken}") }
        }
        return response
    }

    /**
     * Сетевой вызов: обрыв соединения без HTTP-ответа — ошибка сети
     * (S15); повреждённое тело ответа — ошибка формата (S19).
     */
    private suspend fun <T> callNetwork(block: suspend () -> Response<T>): Response<T> =
        try {
            block()
        } catch (e: IOException) {
            throw SyncAbort(SyncError.Network(e))
        } catch (e: SerializationException) {
            throw SyncAbort(SyncError.Format(e))
        }

    /** POST /api/v1/auth/refresh; новая пара сохраняется в хранилище (S12). */
    private suspend fun refreshTokens(
        baseUrl: String,
        refreshToken: String,
    ): TokenPair? {
        val response = try {
            api.refresh(
                "$baseUrl$PATH_REFRESH",
                RefreshRequest(refreshToken = refreshToken),
            )
        } catch (e: IOException) {
            throw SyncAbort(SyncError.Network(e)) // S15
        } catch (e: SerializationException) {
            throw SyncAbort(SyncError.Format(e)) // S19
        }
        val body = response.body()
        return if (response.isSuccessful && body != null) {
            val pair = TokenPair(
                accessToken = body.accessToken,
                refreshToken = body.refreshToken,
            )
            tokenStorage.saveTokens(pair) // S12: новая пара сохраняется
            pair
        } else {
            null // 401 или иная ошибка — refresh не удался (S13)
        }
    }

    // --- Сопоставление ошибок (S15–S19, S26) ---

    private fun <T> Response<T>.ensureSuccess(streamKey: String): T = try {
        when {
            isSuccessful -> body() ?: throw SyncAbort(SyncError.Format(null))
            code() == HTTP_BAD_REQUEST ->
                throw SyncAbort(SyncError.Validation(errorBody()?.string()))
            code() == HTTP_UNAUTHORIZED ->
                throw SyncAbort(SyncError.RequiresLogin)
            code() == HTTP_NOT_FOUND ->
                throw SyncAbort(SyncError.StreamDeleted(streamKey))
            code() == HTTP_PAYLOAD_TOO_LARGE ->
                throw SyncAbort(SyncError.TooManyChanges)
            else -> throw SyncAbort(SyncError.Server(code()))
        }
    } catch (e: SerializationException) {
        // S19: 200 с повреждённым телом — ошибка формата, БД цела.
        throw SyncAbort(SyncError.Format(e))
    }

    private fun DeckEntity.toDeckChange() = DeckChange(
        uuid = id,
        name = name,
        deleted = isDeleted,
    )

    private fun CardEntity.toCardChange() = CardChange(
        uuid = id,
        deckUuid = deckId,
        front = front,
        back = back,
        easeFactor = easinessFactor,
        repetition = repetitionNumber,
        intervalDays = intervalDays,
        dueAt = dueDate,
        deleted = isDeleted,
    )

    private companion object {
        const val PATH_SYNC_DECKS = "/api/v1/sync/decks"
        const val PATH_SYNC_CARDS_PREFIX = "/api/v1/sync/decks/"
        const val PATH_SYNC_CARDS_SUFFIX = "/cards"
        const val PATH_REFRESH = "/api/v1/auth/refresh"

        const val HTTP_BAD_REQUEST = 400
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_NOT_FOUND = 404
        const val HTTP_PAYLOAD_TOO_LARGE = 413
    }
}
