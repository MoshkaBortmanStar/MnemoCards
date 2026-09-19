package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO карточек: CRUD (change add-card-cud) + методы планирования SM-2
 * (change add-sm2-scheduling) + подготовка к синхронизации (change-007).
 *
 * change-007: идентификаторы — UUID (String); мягко удалённые карточки
 * (isDeleted = 1) скрыты из всех запросов для UI, тренировки, счётчиков
 * и экспорта (S21, S22) и не участвуют в отборе назревших. Физическое
 * и мягкое удаление выбирает репозиторий по флагу синхронизации.
 */
@Dao
interface CardDao {

    @Insert
    suspend fun insertCard(card: CardEntity)

    /**
     * Пакетная вставка карточек при импорте CSV (change-006):
     * @Insert со списком выполняется одной транзакцией Room.
     */
    @Insert
    suspend fun insertAll(cards: List<CardEntity>)

    /**
     * Импорт CSV (change-006): проверка колоды и вставка атомарны.
     * Мягко удалённая колода здесь уже невидима (deckExists с фильтром
     * isDeleted = 0), поэтому импорт в неё невозможен (S21).
     */
    @Transaction
    suspend fun insertImportedCards(deckId: String, cards: List<CardEntity>): Boolean {
        if (!deckExists(deckId)) return false
        insertAll(cards)
        return true
    }

    /** 0, если записи нет — основа 404-аналога на уровне репозитория. */
    @Update
    suspend fun updateCard(card: CardEntity): Int

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCard(id: String): Int

    /** Прямой доступ без фильтра isDeleted — для тестов и синхронизации (S23). */
    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getCardByIdIncludingDeleted(id: String): CardEntity?

    /** Все строки колоды без фильтра isDeleted — для тестов и синхронизации (S20). */
    @Query("SELECT * FROM cards WHERE deckId = :deckId")
    suspend fun getCardsByDeckIncludingDeleted(deckId: String): List<CardEntity>

    /** Мягкое удаление: строка остаётся в БД с пометками (S19). */
    @Query(
        """
        UPDATE cards SET isDeleted = 1, isChanged = 1, revision = revision + 1
        WHERE id = :id AND isDeleted = 0
        """
    )
    suspend fun softDeleteCard(id: String): Int

    @Query(
        "SELECT * FROM cards WHERE deckId = :deckId AND isDeleted = 0 " +
            "ORDER BY createdAt DESC, id DESC",
    )
    fun observeCardsByDeck(deckId: String): Flow<List<CardEntity>>

    /** Живые карточки колоды — экспорт пропускает мягко удалённые (S21). */
    @Query(
        "SELECT * FROM cards WHERE deckId = :deckId AND isDeleted = 0 " +
            "ORDER BY createdAt DESC, id DESC",
    )
    suspend fun getCardsByDeck(deckId: String): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getCardById(id: String): CardEntity?

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId AND isDeleted = 0")
    fun observeCardCountByDeck(deckId: String): Flow<Int>

    @Query(
        "SELECT MAX(createdAt) FROM cards WHERE deckId = :deckId AND isDeleted = 0",
    )
    suspend fun maxCreatedAtInDeck(deckId: String): Long?

    /**
     * Отбор в сессию повторения: живые карточки с dueDate <= [now],
     * включая ровно срок (S16), в порядке назревания (S22).
     */
    @Query(
        "SELECT * FROM cards WHERE deckId = :deckId AND isDeleted = 0 " +
            "AND dueDate <= :now ORDER BY dueDate",
    )
    suspend fun getDueCards(deckId: String, now: Long): List<CardEntity>

    /** Счётчик живых карточек, готовых к повторению сегодня (S25, S27). */
    @Query(
        "SELECT COUNT(*) FROM cards WHERE deckId = :deckId AND isDeleted = 0 " +
            "AND dueDate <= :now",
    )
    fun countDue(deckId: String, now: Long): Flow<Int>

    /**
     * Существование живой колоды — для состояния NotFound (S32) и
     * идентификации мягко удалённой колоды с отсутствующей (S9).
     */
    @Query("SELECT EXISTS(SELECT 1 FROM decks WHERE id = :deckId AND isDeleted = 0)")
    suspend fun deckExists(deckId: String): Boolean

    /**
     * Запись результата оценки: новые EF / № повторения / интервал / due
     * (S1). Оценка — правка данных: контракт синхронизации (specs/API.md,
     * «Параметры интервальных повторений… обязательны в каждом изменении»)
     * требует передавать результат занятия на сервер, поэтому запрос
     * помечает карточку изменённой и наращивает revision (change-007).
     */
    @Query(
        """
        UPDATE cards SET
            easinessFactor = :easinessFactor,
            repetitionNumber = :repetitionNumber,
            intervalDays = :intervalDays,
            dueDate = :dueDate,
            isChanged = 1,
            revision = revision + 1
        WHERE id = :cardId
        """,
    )
    suspend fun updateScheduling(
        cardId: String,
        easinessFactor: Double,
        repetitionNumber: Int,
        intervalDays: Int,
        dueDate: Long,
    )
}
