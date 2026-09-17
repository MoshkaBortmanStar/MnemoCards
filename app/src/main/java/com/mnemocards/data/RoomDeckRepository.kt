package com.mnemocards.data

import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.domain.DeckRepository
import com.mnemocards.domain.model.Deck
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-реализация DeckRepository: Room — single source of truth,
 * UI наблюдает изменения через Flow. Ошибки БД пробрасываются наружу (S22);
 * отсутствующий id — no-op (S21): UPDATE/DELETE по несуществующему id
 * в SQL не меняют строк и не создают «призрачных» записей.
 */
class RoomDeckRepository(
    private val deckDao: DeckDao
) : DeckRepository {

    override fun observeDecks(): Flow<List<Deck>> =
        deckDao.observeDecksWithCardCounts().map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun createDeck(name: String): Long =
        deckDao.insertDeck(DeckEntity(name = name.trim()))

    override suspend fun renameDeck(id: Long, name: String) {
        deckDao.renameDeck(id, name.trim())
    }

    override suspend fun deleteDeck(id: Long) {
        deckDao.deleteDeckWithCards(id)
    }

    private fun com.mnemocards.data.db.DeckWithCardCount.toDomain(): Deck =
        Deck(
            id = deckId,
            name = deckName,
            cardCount = cardCount
        )
}
