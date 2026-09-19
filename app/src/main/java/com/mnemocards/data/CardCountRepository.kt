package com.mnemocards.data

import com.mnemocards.data.db.DeckDao
import com.mnemocards.domain.model.DeckCounts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Flow-агрегат (total, dueToday) для списка колод (S25–S27): значения
 * приходят из Room и обновляются автоматически после оценки — без
 * ручного обновления.
 *
 * change-007: читает ту же проекцию [com.mnemocards.data.db.DeckWithStats],
 * что и список колод, — единый источник строк для экрана.
 */
interface DeckCountsRepository {

    fun observeDecksWithCounts(now: Long): Flow<List<DeckCounts>>
}

class RoomDeckCountsRepository(
    private val deckDao: DeckDao,
) : DeckCountsRepository {

    override fun observeDecksWithCounts(now: Long): Flow<List<DeckCounts>> =
        deckDao.observeDecksWithStats(now).map { rows ->
            rows.map { row ->
                DeckCounts(
                    deckId = row.deckId,
                    total = row.total,
                    dueToday = row.dueToday,
                )
            }
        }
}
