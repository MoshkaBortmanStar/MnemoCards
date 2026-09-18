package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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
     * Список колод для UI: мягко удалённые скрыты, счётчик — только
     * по живым карточкам (S21). Порядок — rowid: колоды в порядке
     * создания (числовой id больше не monotonic, rowid остаётся).
     */
    @Query(
        """
        SELECT decks.id AS deckId, decks.name AS deckName,
               (SELECT COUNT(*) FROM cards
                WHERE cards.deckId = decks.id AND cards.isDeleted = 0) AS cardCount
        FROM decks WHERE decks.isDeleted = 0
        ORDER BY decks.rowid ASC
        """
    )
    fun observeDecksWithCardCounts(): Flow<List<DeckWithCardCount>>

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
}
