package com.mnemocards.data

import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import kotlinx.coroutines.flow.Flow

/**
 * Офлайн-репозиторий карточек. Room — единый источник данных,
 * UI наблюдает изменения через Flow.
 */
interface CardRepository {

    /** Карточки колоды, новые сверху (createdAt DESC). */
    fun observeCardsByDeck(deckId: Long): Flow<List<CardEntity>>

    /** Счётчик карточек колоды — обновляется автоматически (S20–S21). */
    fun observeCardCount(deckId: Long): Flow<Int>

    suspend fun getCardById(id: Long): CardEntity?

    /** Создаёт карточку; createdAt гарантирует позицию «первой сверху». */
    suspend fun addCard(deckId: Long, front: String, back: String): Long

    /**
     * Обновляет существующую карточку.
     * @return false, если записи уже нет (404-аналог, S25) —
     *         update несуществующей записи не превращается в insert.
     */
    suspend fun updateCard(card: CardEntity): Boolean

    /** @return false, если записи уже нет (404-аналог). */
    suspend fun deleteCard(cardId: Long): Boolean
}

class RoomCardRepository(
    private val cardDao: CardDao,
) : CardRepository {

    override fun observeCardsByDeck(deckId: Long): Flow<List<CardEntity>> =
        cardDao.observeCardsByDeck(deckId)

    override fun observeCardCount(deckId: Long): Flow<Int> =
        cardDao.observeCardCountByDeck(deckId)

    override suspend fun getCardById(id: Long): CardEntity? =
        cardDao.getCardById(id)

    override suspend fun addCard(deckId: Long, front: String, back: String): Long {
        // Строго возрастающий createdAt внутри колоды: новые всегда выше старых.
        val max = cardDao.maxCreatedAtInDeck(deckId) ?: 0L
        val now = System.currentTimeMillis()
        val createdAt = if (now > max) now else max + 1
        return cardDao.insertCard(
            CardEntity(deckId = deckId, front = front, back = back, createdAt = createdAt)
        )
    }

    override suspend fun updateCard(card: CardEntity): Boolean =
        cardDao.updateCard(card) > 0

    override suspend fun deleteCard(cardId: Long): Boolean =
        cardDao.deleteCard(cardId) > 0
}
