package com.mnemocards.data.repository

import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckWithCardCount
import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.model.ReviewCard
import com.mnemocards.domain.repository.DeckRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Имплементация [DeckRepository] поверх [DeckDao] (Room).
 *
 * Отвечает за:
 * - маппинг сущностей БД в доменные модели (UI не знает о Room);
 * - оборачивание исключений источника данных (закрытая/повреждённая БД,
 *   ошибка чтения) в ошибочный результат `Result.failure` (S9) —
 *   необработанный крах до UI не доходит.
 *
 * Room-Flow самодостаточен по потокобезопасности (main-safe),
 * смена диспетчера репозиторием не требуется.
 */
class DeckRepositoryImpl(
    private val deckDao: DeckDao,
) : DeckRepository {

    override fun observeDecks(): Flow<Result<List<Deck>>> =
        deckDao.observeDecksWithCount()
            .map { rows -> Result.success(rows.map { it.toDeck() }) }
            .catch { throwable -> emit(Result.failure(throwable)) }

    override fun observeCards(deckId: Long): Flow<Result<List<ReviewCard>>> =
        deckDao.observeCardsByDeckId(deckId)
            .map { entities -> Result.success(entities.map { it.toReviewCard() }) }
            .catch { throwable -> emit(Result.failure(throwable)) }

    private fun DeckWithCardCount.toDeck(): Deck = Deck(
        id = deckId,
        name = deckName,
        cardCount = cardCount,
    )

    private fun CardEntity.toReviewCard(): ReviewCard = ReviewCard(
        id = id,
        front = front,
        back = back,
    )
}
