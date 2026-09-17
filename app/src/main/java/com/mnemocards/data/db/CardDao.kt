package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO карточек: CRUD (change add-card-cud) + методы планирования SM-2
 * (change add-sm2-scheduling): отбор по сроку, счётчик готовых сегодня
 * и запись результата оценки.
 */
@Dao
interface CardDao {

    @Insert
    suspend fun insertCard(card: CardEntity): Long

    /**
     * Пакетная вставка карточек при импорте CSV (change-006):
     * @Insert со списком выполняется одной транзакцией Room.
     */
    @Insert
    suspend fun insertAll(cards: List<CardEntity>): List<Long>

    /**
     * Импорт CSV (change-006): проверка колоды и вставка атомарны.
     * Колода удалена «между» проверкой и вставкой → false, ничего не
     * вставлено (S21); сбой вставки откатывает весь пакет (S22).
     */
    @Transaction
    suspend fun insertImportedCards(deckId: Long, cards: List<CardEntity>): Boolean {
        if (!deckExists(deckId)) return false
        insertAll(cards)
        return true
    }

    /** 0, если записи нет — основа 404-аналога на уровне репозитория. */
    @Update
    suspend fun updateCard(card: CardEntity): Int

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCard(id: Long): Int

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAt DESC, id DESC")
    fun observeCardsByDeck(deckId: Long): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAt DESC, id DESC")
    suspend fun getCardsByDeck(deckId: Long): List<CardEntity>

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: Long): CardEntity?

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId")
    fun observeCardCountByDeck(deckId: Long): Flow<Int>

    @Query("SELECT MAX(createdAt) FROM cards WHERE deckId = :deckId")
    suspend fun maxCreatedAtInDeck(deckId: Long): Long?

    /**
     * Отбор в сессию повторения: карточки с dueDate <= [now], включая
     * ровно срок (S16), в порядке назревания.
     */
    @Query("SELECT * FROM cards WHERE deckId = :deckId AND dueDate <= :now ORDER BY dueDate")
    suspend fun getDueCards(deckId: Long, now: Long): List<CardEntity>

    /** Счётчик карточек, готовых к повторению сегодня (S25, S27). */
    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId AND dueDate <= :now")
    fun countDue(deckId: Long, now: Long): Flow<Int>

    /**
     * Агрегат для списка колод одним запросом (S25–S27): общее количество
     * карточек и готовых к повторению сегодня по каждой колоде.
     */
    @Query(
        """
        SELECT decks.id AS deckId,
               COUNT(cards.id) AS total,
               SUM(CASE WHEN cards.dueDate <= :now THEN 1 ELSE 0 END) AS dueToday
        FROM decks LEFT JOIN cards ON cards.deckId = decks.id
        GROUP BY decks.id
        ORDER BY decks.id
        """,
    )
    fun observeDeckCounts(now: Long): Flow<List<DeckWithCardCounts>>

    /** Существование колоды — для состояния NotFound (S32). */
    @Query("SELECT EXISTS(SELECT 1 FROM decks WHERE id = :deckId)")
    suspend fun deckExists(deckId: Long): Boolean

    /** Запись результата оценки: новые EF / № повторения / интервал / due (S1). */
    @Query(
        """
        UPDATE cards SET
            easinessFactor = :easinessFactor,
            repetitionNumber = :repetitionNumber,
            intervalDays = :intervalDays,
            dueDate = :dueDate
        WHERE id = :cardId
        """,
    )
    suspend fun updateScheduling(
        cardId: Long,
        easinessFactor: Double,
        repetitionNumber: Int,
        intervalDays: Int,
        dueDate: Long,
    )
}
