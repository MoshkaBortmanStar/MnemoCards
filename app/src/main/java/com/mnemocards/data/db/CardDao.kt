package com.mnemocards.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Insert
    suspend fun insertCard(card: CardEntity): Long

    /** 0, если записи нет — основа 404-аналога на уровне репозитория. */
    @Update
    suspend fun updateCard(card: CardEntity): Int

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun deleteCard(id: Long): Int

    @Query("SELECT * FROM cards WHERE deckId = :deckId ORDER BY createdAt DESC, id DESC")
    fun observeCardsByDeck(deckId: Long): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :id LIMIT 1")
    suspend fun getCardById(id: Long): CardEntity?

    @Query("SELECT COUNT(*) FROM cards WHERE deckId = :deckId")
    fun observeCardCountByDeck(deckId: Long): Flow<Int>

    @Query("SELECT MAX(createdAt) FROM cards WHERE deckId = :deckId")
    suspend fun maxCreatedAtInDeck(deckId: Long): Long?
}
