package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Проекция «колода + количество карточек» для списка колод на стартовом экране.
 */
data class DeckWithCardCount(
    val deckId: Long,
    val deckName: String,
    val cardCount: Int,
)

@Dao
interface DeckDao {

    @Insert
    suspend fun insertDeck(deck: DeckEntity): Long

    @Insert
    suspend fun insertCards(cards: List<CardEntity>)

    /**
     * Транзакционная вставка колоды вместе с её карточками:
     * колода и карточки попадают в БД атомарно. Возвращает id колоды.
     */
    @Transaction
    suspend fun insertDeckWithCards(deck: DeckEntity, cards: List<CardEntity>): Long {
        val deckId = insertDeck(deck)
        insertCards(cards.map { card -> card.copy(deckId = deckId) })
        return deckId
    }

    /**
     * Список колод с количеством карточек (LEFT JOIN + COUNT, группировка по колоде).
     */
    @Query(
        """
        SELECT decks.id AS deckId,
               decks.name AS deckName,
               COUNT(cards.id) AS cardCount
        FROM decks
        LEFT JOIN cards ON cards.deckId = decks.id
        GROUP BY decks.id
        ORDER BY decks.id
        """
    )
    fun observeDecksWithCount(): Flow<List<DeckWithCardCount>>

    @Query("SELECT * FROM decks WHERE id = :deckId")
    suspend fun getDeckById(deckId: Long): DeckEntity?

    /**
     * Карточки только выбранной колоды — источник данных сессии `cards.review`.
     */
    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY id")
    fun observeCardsByDeckId(deckId: Long): Flow<List<CardEntity>>

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId")
    suspend fun cardCountForDeck(deckId: Long): Int

    /**
     * Guard для идемпотентного сидинга: пуста ли таблица колод.
     */
    @Query("SELECT COUNT(*) FROM decks")
    suspend fun deckCount(): Int

    /**
     * Атомарный guard-сидинг: вставка базовой колоды, только если таблица
     * колод пуста (S1, S2). Возвращает true, если вставка выполнена.
     */
    @Transaction
    suspend fun insertDeckWithCardsIfAbsent(
        deck: DeckEntity,
        cards: List<CardEntity>,
    ): Boolean {
        if (deckCount() != 0) return false
        insertDeckWithCards(deck, cards)
        return true
    }
}
