package com.mnemocards.data.repository

import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch

/**
 * Результат операции записи с карточкой.
 * 404-аналог — [CardOperationResult.NotFound] (записи уже нет,
 * update несуществующей записи не превращается в insert).
 * 500-аналог — [CardOperationResult.Error] (исключение Room поймано,
 * приложение не падает).
 */
sealed interface CardOperationResult {
    data class Success(val cardId: Long) : CardOperationResult
    object NotFound : CardOperationResult
    data class Error(val cause: Throwable? = null) : CardOperationResult
}

/**
 * Офлайн-репозиторий карточек. Room — единый источник данных,
 * UI наблюдает изменения через Flow.
 */
interface CardRepository {

    /** Колода по идентификатору; null — 404-аналог (S24). */
    fun observeDeck(deckId: Long): Flow<DeckEntity?>

    /** Карточки колоды, новые сверху (createdAt DESC). */
    fun observeCards(deckId: Long): Flow<List<CardEntity>>

    /** Счётчик карточек колоды — обновляется автоматически (S20–S21). */
    fun observeCardCount(deckId: Long): Flow<Int>

    suspend fun getCardById(id: Long): CardEntity?

    /** Создаёт карточку; createdAt гарантирует позицию «первой сверху». */
    suspend fun addCard(deckId: Long, front: String, back: String): CardOperationResult

    /**
     * Обновляет существующую карточку (front/back), сохраняя deckId и createdAt.
     * [CardOperationResult.NotFound] — записи уже нет (S25):
     * update несуществующей записи не превращается в insert.
     */
    suspend fun updateCard(cardId: Long, front: String, back: String): CardOperationResult

    suspend fun deleteCard(cardId: Long): CardOperationResult
}

class RoomCardRepository(
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
) : CardRepository {

    override fun observeDeck(deckId: Long): Flow<DeckEntity?> =
        deckDao.observeDeckById(deckId)

    override fun observeCards(deckId: Long): Flow<List<CardEntity>> =
        cardDao.observeCardsByDeck(deckId)
            .catch { throw it } // ошибки чтения пробрасываются в Flow -> UiState.Error (S26)

    override fun observeCardCount(deckId: Long): Flow<Int> =
        cardDao.observeCardCountByDeck(deckId)
            .catch { throw it }

    override suspend fun getCardById(id: Long): CardEntity? =
        cardDao.getCardById(id)

    override suspend fun addCard(
        deckId: Long,
        front: String,
        back: String,
    ): CardOperationResult = try {
        // Строго возрастающий createdAt внутри колоды: новые всегда выше старых.
        val max = cardDao.maxCreatedAtInDeck(deckId) ?: 0L
        val now = System.currentTimeMillis()
        val createdAt = if (now > max) now else max + 1
        val id = cardDao.insertCard(
            CardEntity(deckId = deckId, front = front, back = back, createdAt = createdAt)
        )
        CardOperationResult.Success(id)
    } catch (e: Exception) {
        CardOperationResult.Error(e)
    }

    override suspend fun updateCard(
        cardId: Long,
        front: String,
        back: String,
    ): CardOperationResult = try {
        val existing = cardDao.getCardById(cardId)
            ?: return CardOperationResult.NotFound
        val updated = cardDao.updateCard(existing.copy(front = front, back = back))
        if (updated > 0) {
            CardOperationResult.Success(cardId)
        } else {
            CardOperationResult.NotFound
        }
    } catch (e: Exception) {
        CardOperationResult.Error(e)
    }

    override suspend fun deleteCard(cardId: Long): CardOperationResult = try {
        if (cardDao.deleteCard(cardId) > 0) {
            CardOperationResult.Success(cardId)
        } else {
            CardOperationResult.NotFound
        }
    } catch (e: Exception) {
        CardOperationResult.Error(e)
    }
}
