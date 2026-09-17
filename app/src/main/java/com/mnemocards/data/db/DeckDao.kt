package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckDao {

    @Insert
    suspend fun insertDeck(deck: DeckEntity): Long

    @Query("SELECT * FROM decks WHERE id = :id LIMIT 1")
    suspend fun getDeckById(id: Long): DeckEntity?

    @Query("SELECT * FROM decks WHERE name = :name LIMIT 1")
    suspend fun getDeckByName(name: String): DeckEntity?

    /** Потоковое чтение колоды для экрана содержимого (S1, S24). */
    @Query("SELECT * FROM decks WHERE id = :id LIMIT 1")
    fun observeDeckById(id: Long): Flow<DeckEntity?>

    @Query(
        """
        SELECT decks.id AS deckId, decks.name AS deckName,
               (SELECT COUNT(*) FROM cards WHERE cards.deckId = decks.id) AS cardCount
        FROM decks ORDER BY decks.id ASC
        """
    )
    fun observeDecksWithCardCounts(): Flow<List<DeckWithCardCount>>
}
