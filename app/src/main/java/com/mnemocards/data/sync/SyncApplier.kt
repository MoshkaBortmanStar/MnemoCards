package com.mnemocards.data.sync

import androidx.room.withTransaction
import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.db.sync.SyncStateEntity
import com.mnemocards.data.network.dto.models.CardRecord
import com.mnemocards.data.network.dto.models.DeckRecord

/**
 * Применение успешного ответа синхронизации к Room (change-009, T8).
 * Все операции — в транзакции: либо ответ применён целиком, либо
 * локальная БД не изменилась (S19).
 *
 * Правила:
 *  - серверные записи перезаписывают локальные поля, ревизия потока
 *    сохраняется из ответа (S6);
 *  - запись с признаком удаления (deletedAt != null) удаляется
 *    физически, tombstone не создаётся (S7);
 *  - fullSync=true — полная замена состояния потока: локальные записи,
 *    отсутствующие в ответе, удаляются (S8);
 *  - серверная запись не применяется, если её локальная копия изменена
 *    после снапшота отправки (S25, S10);
 *  - признак локального изменения снимается только при совпадении
 *    текущего счётчика правок (revision) со снапшотом, зафиксированным
 *    при отправке (S9);
 *  - tombstone, подтверждённый сервером, удаляется физически (S11);
 *  - повторное применение того же ответа не создаёт дублей — ключи
 *    записей — UUID сервера (S24).
 *
 * Синх-поля записей — isChanged/revision/isDeleted из change-007:
 * isChanged == pendingChanges, revision == счётчик правок (editCounter),
 * isDeleted == tombstone.
 */
class SyncApplier(
    private val db: MnemoCardsDatabase,
) {

    /**
     * Применяет ответ потока колод.
     *
     * @param snapshot счётчики правок (revision) отправленных записей,
     * зафиксированные в момент отправки: uuid колоды -> revision.
     */
    suspend fun applyDecksResponse(
        records: List<DeckRecord>,
        fullSync: Boolean,
        revision: Long,
        snapshot: Map<String, Int> = emptyMap(),
    ) {
        val deckDao = db.deckDao()
        db.withTransaction {
            val aliveIds = mutableSetOf<String>()
            for (record in records) {
                if (record.deletedAt != null) {
                    // S7: удалена на сервере — физическое удаление без
                    // tombstone; карточки удаляются каскадом FK.
                    deckDao.deleteDeckById(record.uuid)
                    continue
                }
                aliveIds += record.uuid
                val local = deckDao.getDeckByIdIncludingDeleted(record.uuid)
                val sentRevision = snapshot[record.uuid]
                when {
                    // Локальной копии нет — вставка серверной записи (S4, S6).
                    local == null -> deckDao.insertDeck(record.toEntity())
                    // Локальная копия не менялась с отправки — подтверждение (S9, S6).
                    sentRevision != null && local.revision == sentRevision ->
                        deckDao.updateDeck(
                            local.overwrittenBy(record).copy(isChanged = false),
                        )
                    // Непомеченная локальная копия — перезапись сервером (S6).
                    !local.isChanged -> deckDao.updateDeck(local.overwrittenBy(record))
                    // S25, S10: изменена после снапшота — серверная версия
                    // не применяется, локальная правка сохраняется.
                    else -> Unit
                }
            }
            if (fullSync) {
                // S8: полный срез — локальные записи вне ответа удаляются,
                // кроме изменённых после снапшота отправки.
                for (local in deckDao.getDecksIncludingDeleted()) {
                    if (local.id !in aliveIds && !isProtectedByRevision(local.id, local.revision, snapshot)) {
                        deckDao.deleteDeckById(local.id)
                    }
                }
            }
            confirmDecks(deckDao, snapshot)
            db.syncStateDao().saveRevision(SyncStateEntity(STREAM_DECKS, revision))
        }
    }

    /**
     * Применяет ответ потока карточек колоды [deckId].
     *
     * @param snapshot счётчики правок отправленных карточек: uuid -> revision.
     */
    suspend fun applyCardsResponse(
        deckId: String,
        records: List<CardRecord>,
        fullSync: Boolean,
        revision: Long,
        snapshot: Map<String, Int> = emptyMap(),
    ) {
        val cardDao = db.cardDao()
        db.withTransaction {
            val aliveIds = mutableSetOf<String>()
            for (record in records) {
                if (record.deletedAt != null) {
                    cardDao.deleteCard(record.uuid) // S7
                    continue
                }
                aliveIds += record.uuid
                val local = cardDao.getCardByIdIncludingDeleted(record.uuid)
                val sentRevision = snapshot[record.uuid]
                when {
                    local == null -> cardDao.insertCard(record.toEntity())
                    sentRevision != null && local.revision == sentRevision ->
                        cardDao.updateCard(
                            local.overwrittenBy(record).copy(isChanged = false),
                        )
                    !local.isChanged -> cardDao.updateCard(local.overwrittenBy(record))
                    else -> Unit // S25, S10
                }
            }
            if (fullSync) {
                for (local in cardDao.getCardsByDeckIncludingDeleted(deckId)) {
                    if (local.id !in aliveIds && !isProtectedByRevision(local.id, local.revision, snapshot)) {
                        cardDao.deleteCard(local.id)
                    }
                }
            }
            confirmCards(cardDao, snapshot)
            db.syncStateDao().saveRevision(SyncStateEntity(cardsStream(deckId), revision))
        }
    }

    /**
     * Подтверждение отправленных колод по снапшоту: tombstone удаляется
     * физически (S11), изменённые в полёте записи сохраняют признак
     * локального изменения (S10), остальные — снимают его (S9).
     */
    private suspend fun confirmDecks(
        deckDao: DeckDao,
        snapshot: Map<String, Int>,
    ) {
        for ((id, sentRevision) in snapshot) {
            val local = deckDao.getDeckByIdIncludingDeleted(id) ?: continue
            if (local.revision != sentRevision) continue // S10
            if (local.isDeleted) {
                deckDao.deleteDeckById(id) // S11
            } else {
                deckDao.clearPendingIfUnchanged(id, sentRevision) // S9
            }
        }
    }

    /** Подтверждение отправленных карточек — правила как в [confirmDecks]. */
    private suspend fun confirmCards(
        cardDao: CardDao,
        snapshot: Map<String, Int>,
    ) {
        for ((id, sentRevision) in snapshot) {
            val local = cardDao.getCardByIdIncludingDeleted(id) ?: continue
            if (local.revision != sentRevision) continue // S10
            if (local.isDeleted) {
                cardDao.deleteCard(id) // S11
            } else {
                cardDao.clearPendingIfUnchanged(id, sentRevision) // S9
            }
        }
    }

    /** Изменена после снапшота отправки — локальная правка важнее сервера. */
    private fun isProtectedByRevision(
        id: String,
        currentRevision: Int,
        snapshot: Map<String, Int>,
    ): Boolean = snapshot[id] != null && snapshot[id] != currentRevision

    private fun DeckRecord.toEntity() = DeckEntity(
        id = uuid,
        name = name,
        isChanged = false,
        revision = 0,
        isDeleted = false,
    )

    private fun DeckEntity.overwrittenBy(record: DeckRecord) = copy(
        name = record.name,
        isDeleted = false,
    )

    private fun CardRecord.toEntity() = CardEntity(
        id = uuid,
        deckId = deckUuid,
        front = front,
        back = back,
        // createdAt на сервере не хранится — берем момент последнего обновления.
        createdAt = updatedAt,
        easinessFactor = easeFactor,
        repetitionNumber = repetition,
        intervalDays = intervalDays,
        dueDate = dueAt,
        isChanged = false,
        revision = 0,
        isDeleted = false,
    )

    private fun CardEntity.overwrittenBy(record: CardRecord) = copy(
        deckId = record.deckUuid,
        front = record.front,
        back = record.back,
        easinessFactor = record.easeFactor,
        repetitionNumber = record.repetition,
        intervalDays = record.intervalDays,
        dueDate = record.dueAt,
        isDeleted = false,
    )

    companion object {
        const val STREAM_DECKS = "decks"

        /** Ключ потока карточек колоды: cards:{deckUuid} (S3, S24). */
        fun cardsStream(deckId: String): String = "cards:$deckId"
    }
}
