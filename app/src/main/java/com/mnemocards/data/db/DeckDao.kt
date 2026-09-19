package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO колод (change-007): идентификаторы — UUID (String).
 *
 * Мягко удалённые колоды (isDeleted = 1) скрыты из всех запросов для UI
 * (S21) и считаются отсутствующими (S9). Физическое удаление выполняется
 * только при выключенном флаге синхронизации, мягкое — при включённом;
 * выбор режима — в репозитории.
 */
@Dao
interface DeckDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertDeck(deck: DeckEntity)

    /**
     * Прямая перезапись колоды без пометки локального изменения —
     * применяется к серверным записям при синхронизации (change-009, S6).
     */
    @Update
    suspend fun updateDeck(deck: DeckEntity)

    @Query("SELECT * FROM decks WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getDeckById(id: String): DeckEntity?

    @Query("SELECT * FROM decks WHERE name = :name AND isDeleted = 0 LIMIT 1")
    suspend fun getDeckByName(name: String): DeckEntity?

    /** Потоковое чтение колоды для экрана содержимого (S1, S24). */
    @Query("SELECT * FROM decks WHERE id = :id AND isDeleted = 0 LIMIT 1")
    fun observeDeckById(id: String): Flow<DeckEntity?>

    /** Прямой доступ без фильтра isDeleted — для тестов и синхронизации. */
    @Query("SELECT * FROM decks WHERE id = :id LIMIT 1")
    suspend fun getDeckByIdIncludingDeleted(id: String): DeckEntity?

    /**
     * Строки списка колод (S21): мягко удалённые колоды скрыты, счётчики —
     * только по живым карточкам; dueToday — живые карточки с
     * dueDate <= [now] (S25–S27). Порядок — rowid: колоды в порядке
     * создания (числовой id больше не monotonic, rowid остаётся).
     */
    @Query(
        """
        SELECT decks.id AS deckId, decks.name AS deckName,
               COUNT(cards.id) AS total,
               IFNULL(SUM(CASE WHEN cards.dueDate <= :now THEN 1 ELSE 0 END), 0) AS dueToday
        FROM decks
        LEFT JOIN cards ON cards.deckId = decks.id AND cards.isDeleted = 0
        WHERE decks.isDeleted = 0
        GROUP BY decks.id
        ORDER BY decks.rowid ASC
        """
    )
    fun observeDecksWithStats(now: Long): Flow<List<DeckWithStats>>

    /**
     * Переименование — локальная правка: ставит isChanged = 1 и
     * увеличивает revision (S11).
     */
    @Query(
        """
        UPDATE decks SET name = :name, isChanged = 1, revision = revision + 1
        WHERE id = :id AND isDeleted = 0
        """
    )
    suspend fun renameDeck(id: String, name: String)

    @Query("DELETE FROM cards WHERE deckId = :deckId")
    suspend fun deleteCardsByDeckId(deckId: String)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun deleteDeckById(id: String)

    /** Физическое удаление колоды вместе с карточками — единой операцией (S18). */
    @Transaction
    suspend fun deleteDeckWithCards(id: String) {
        deleteCardsByDeckId(id)
        deleteDeckById(id)
    }

    /**
     * Мягкое удаление колоды (S20): строка остаётся в БД с пометками,
     * из запросов UI исчезает.
     */
    @Query(
        """
        UPDATE decks SET isDeleted = 1, isChanged = 1, revision = revision + 1
        WHERE id = :id AND isDeleted = 0
        """
    )
    suspend fun softDeleteDeckById(id: String)

    /** Мягкое удаление карточек колоды — каскад мягкого удаления колоды (S20). */
    @Query(
        """
        UPDATE cards SET isDeleted = 1, isChanged = 1, revision = revision + 1
        WHERE deckId = :deckId AND isDeleted = 0
        """
    )
    suspend fun softDeleteCardsByDeckId(deckId: String)

    /**
     * Мягкое удаление колоды вместе со всеми карточками — единой
     * операцией (S20).
     */
    @Transaction
    suspend fun softDeleteDeckWithCards(id: String) {
        softDeleteCardsByDeckId(id)
        softDeleteDeckById(id)
    }

    // --- Фоновая синхронизация (change-009) ---

    /** Колоды с незавершённой отправкой, включая tombstone (S5). */
    @Query("SELECT * FROM decks WHERE isChanged = 1")
    suspend fun getPendingDecks(): List<DeckEntity>

    /** Все колоды без фильтра — полная замена потока при fullSync (S8). */
    @Query("SELECT * FROM decks")
    suspend fun getDecksIncludingDeleted(): List<DeckEntity>

    /** Живые колоды — потоки карточек следующей сессии (S3). */
    @Query("SELECT id FROM decks WHERE isDeleted = 0")
    suspend fun getAliveDeckIds(): List<String>

    /**
     * Снимает признак локального изменения, только если счётчик правок
     * не менялся с момента отправки (S9); правка в полёте сохраняет
     * признак (S10). Возвращает число изменённых строк.
     */
    @Query(
        """
        UPDATE decks SET isChanged = 0
        WHERE id = :id AND revision = :sentRevision
        """
    )
    suspend fun clearPendingIfUnchanged(id: String, sentRevision: Int): Int
}
