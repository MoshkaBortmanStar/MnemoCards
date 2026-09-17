package com.mnemocards.data

import com.mnemocards.data.db.CardDao
import com.mnemocards.domain.model.DeckCounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Flow-агрегат (total, dueToday) для списка колод (S25–S27): значения
 * приходят из Room и обновляются автоматически после оценки — без
 * ручного обновления.
 */
interface DeckCountsRepository {

    fun observeDecksWithCounts(now: Long): Flow<List<DeckCounts>>
}

class RoomDeckCountsRepository(
    private val cardDao: CardDao,
) : DeckCountsRepository {

    override fun observeDecksWithCounts(now: Long): Flow<List<DeckCounts>> =
        cardDao.observeDeckCounts(now).map { rows ->
            rows.map { row ->
                DeckCounts(
                    deckId = row.deckId,
                    total = row.total,
                    dueToday = row.dueToday,
                )
            }
        }
}
