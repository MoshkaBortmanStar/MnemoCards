package com.mnemocards.data.repository

import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.sync.SyncFlagDataStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * Результат операции записи с карточкой.
 * 404-аналог — [CardOperationResult.NotFound] (записи уже нет,
 * update несуществующей записи не превращается в insert).
 * 500-аналог — [CardOperationResult.Error] (исключение Room поймано,
 * приложение не падает).
 */
sealed interface CardOperationResult {
    data class Success(val cardId: String) : CardOperationResult
    object NotFound : CardOperationResult
    data class Error(val cause: Throwable? = null) : CardOperationResult
}

/**
 * Результат импорта карточек из CSV (change add-csv-import-export).
 */
sealed interface CardImportResult {
    data class Imported(val added: Int) : CardImportResult
    data object DeckNotFound : CardImportResult
}

/**
 * Офлайн-репозиторий карточек. Room — единый источник данных,
 * UI наблюдает изменения через Flow.
 *
 * change-007: идентификаторы — UUID (String), генерируются при
 * создании (S6); изменение карточки ставит isChanged = true и
 * revision + 1 (S11, S13); удаление ветвится по флагу синхронизации:
 * физическое (S17) или мягкое (S19). Мягко удалённые записи скрыты
 * из всех запросов для UI и экспорта (S21).
 */
interface CardRepository {

    /** Живая колода по идентификатору; null — 404-аналог (S24, S9). */
    fun observeDeck(deckId: String): Flow<DeckEntity?>

    /** Живые карточки колоды, новые сверху (createdAt DESC). */
    fun observeCards(deckId: String): Flow<List<CardEntity>>

    /** Счётчик живых карточек колоды — обновляется автоматически (S20–S21). */
    fun observeCardCount(deckId: String): Flow<Int>

    suspend fun getCardById(id: String): CardEntity?

    /** Создаёт карточку с клиентским UUID; createdAt — позиция «первой сверху». */
    suspend fun addCard(deckId: String, front: String, back: String): CardOperationResult

    /**
     * Обновляет существующую карточку (front/back), сохраняя deckId и
     * createdAt. Ставит isChanged = true, revision + 1 (S11).
     * [CardOperationResult.NotFound] — записи уже нет (S25).
     */
    suspend fun updateCard(cardId: String, front: String, back: String): CardOperationResult

    /**
     * Удаляет карточку: физически при выключенном флаге синхронизации
     * (S17), мягко — при включённом (S19).
     */
    suspend fun deleteCard(cardId: String): CardOperationResult

    /**
     * Экспорт колоды в CSV (change-006): только тексты сторон живых
     * карточек — идентификаторы и параметры SRS не выгружаются (S5, S25,
     * S21). `null` — колода не найдена.
     */
    suspend fun exportCardTexts(deckId: String): List<Pair<String, String>>?

    /**
     * Импорт карточек из CSV (change-006): все валидные строки
     * добавляются как новые без дедупликации, атомарно (S9, S15, S22);
     * несуществующая колода — [CardImportResult.DeckNotFound] (S21).
     */
    suspend fun importCards(
        deckId: String,
        cards: List<Pair<String, String>>,
    ): CardImportResult
}

class RoomCardRepository(
    private val deckDao: DeckDao,
    private val cardDao: CardDao,
    private val syncFlagDataStore: SyncFlagDataStore? = null,
) : CardRepository {

    override fun observeDeck(deckId: String): Flow<DeckEntity?> =
        deckDao.observeDeckById(deckId)

    override fun observeCards(deckId: String): Flow<List<CardEntity>> =
        cardDao.observeCardsByDeck(deckId)
            .catch { throw it } // ошибки чтения пробрасываются в Flow -> UiState.Error (S26)

    override fun observeCardCount(deckId: String): Flow<Int> =
        cardDao.observeCardCountByDeck(deckId)
            .catch { throw it }

    override suspend fun getCardById(id: String): CardEntity? =
        cardDao.getCardById(id)

    override suspend fun addCard(
        deckId: String,
        front: String,
        back: String,
    ): CardOperationResult = try {
        // Строго возрастающий createdAt внутри колоды: новые всегда выше старых.
        val max = cardDao.maxCreatedAtInDeck(deckId) ?: 0L
        val now = System.currentTimeMillis()
        val createdAt = if (now > max) now else max + 1
        val id = UUID.randomUUID().toString()
        cardDao.insertCard(
            // S24: новая карточка без истории готова к повторению сразу —
            // дефолты планирования SM-2, dueDate = «сейчас».
            // S6/S10: клиентский UUID, isChanged = true, revision = 1.
            CardEntity(
                id = id,
                deckId = deckId,
                front = front,
                back = back,
                createdAt = createdAt,
                dueDate = now,
                isChanged = true,
                revision = 1,
                isDeleted = false,
            ),
        )
        CardOperationResult.Success(id)
    } catch (e: Exception) {
        CardOperationResult.Error(e)
    }

    override suspend fun updateCard(
        cardId: String,
        front: String,
        back: String,
    ): CardOperationResult = try {
        val existing = cardDao.getCardById(cardId)
            ?: return CardOperationResult.NotFound
        // S11/S13: правка помечает запись изменённой и наращивает счётчик.
        val updated = cardDao.updateCard(
            existing.copy(
                front = front,
                back = back,
                isChanged = true,
                revision = existing.revision + 1,
            ),
        )
        if (updated > 0) {
            CardOperationResult.Success(cardId)
        } else {
            CardOperationResult.NotFound
        }
    } catch (e: Exception) {
        CardOperationResult.Error(e)
    }

    override suspend fun deleteCard(cardId: String): CardOperationResult = try {
        val deleted = if (isSyncEnabled()) {
            // S19: мягкое удаление — строка остаётся в БД с пометками.
            cardDao.softDeleteCard(cardId)
        } else {
            // S17: физическое удаление, как в версии до change-007.
            cardDao.deleteCard(cardId)
        }
        if (deleted > 0) {
            CardOperationResult.Success(cardId)
        } else {
            CardOperationResult.NotFound
        }
    } catch (e: Exception) {
        CardOperationResult.Error(e)
    }

    /**
     * Экспорт (S5, S25): только тексты сторон живых карточек (S21) —
     * идентификаторы и параметры SRS в CSV не попадают. `null` — колода
     * не найдена.
     */
    override suspend fun exportCardTexts(deckId: String): List<Pair<String, String>>? {
        if (!cardDao.deckExists(deckId)) return null
        return cardDao.getCardsByDeck(deckId).map { it.front to it.back }
    }

    /**
     * Импорт (S9, S15, S16, S21, S22): все валидные строки — новые
     * карточки с клиентскими UUID без дедупликации; состояние «новой
     * карточки» как при ручном создании (дефолты SM-2, dueDate =
     * «сейчас» — готовы к повторению сразу, S14; isChanged = true,
     * revision = 1). createdAt строго возрастает внутри колоды
     * (зеркалит [addCard]), импортированный пакет встаёт сверху списка
     * в порядке файла. Ошибка БД пробрасывается наружу, транзакция DAO
     * откатывает пакет.
     */
    override suspend fun importCards(
        deckId: String,
        cards: List<Pair<String, String>>,
    ): CardImportResult {
        val max = cardDao.maxCreatedAtInDeck(deckId) ?: 0L
        val now = System.currentTimeMillis()
        val entities = cards.mapIndexed { index, (front, back) ->
            val createdAt = if (now > max) now + index else max + 1 + index
            CardEntity(
                id = UUID.randomUUID().toString(),
                deckId = deckId,
                front = front,
                back = back,
                createdAt = createdAt,
                dueDate = now,
                isChanged = true,
                revision = 1,
                isDeleted = false,
            )
        }
        return if (cardDao.insertImportedCards(deckId, entities)) {
            CardImportResult.Imported(entities.size)
        } else {
            CardImportResult.DeckNotFound
        }
    }

    /**
     * S16: ошибка чтения DataStore трактуется как выключенный флаг —
     * удаление выполняется в дефолтном (физическом) режиме.
     */
    private suspend fun isSyncEnabled(): Boolean =
        syncFlagDataStore?.let { store ->
            runCatching { store.syncEnabled.first() }.getOrDefault(false)
        } ?: false
}
