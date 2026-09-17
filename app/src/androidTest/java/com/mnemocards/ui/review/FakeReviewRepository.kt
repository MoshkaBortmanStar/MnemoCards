package com.mnemocards.ui.review

import com.mnemocards.data.HardcodedDeck
import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.model.ReviewCard
import com.mnemocards.domain.repository.DeckRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Фейковый [DeckRepository] для инструментальных тестов экрана тренировки
 * (change-002: `ReviewViewModel` читает карточки из репозитория по `deckId`,
 * а не из захардкоженной колоды).
 *
 * Отдаёт те же смешанные пары из [HardcodedDeck]: письменности (S10)
 * и длинная пятая карточка (S11) остаются покрытыми.
 */
class FakeReviewRepository : DeckRepository {

    override fun observeDecks(): Flow<Result<List<Deck>>> = flowOf(
        Result.success(
            listOf(
                Deck(
                    id = REVIEW_DECK_ID,
                    name = "Базовая колода",
                    cardCount = HardcodedDeck.cards.size,
                ),
            ),
        ),
    )

    override fun observeCards(deckId: Long): Flow<Result<List<ReviewCard>>> = flowOf(
        Result.success(
            if (deckId == REVIEW_DECK_ID) reviewCards else emptyList(),
        ),
    )

    private val reviewCards: List<ReviewCard> = HardcodedDeck.cards.mapIndexed { index, card ->
        ReviewCard(id = index.toLong(), front = card.front, back = card.back)
    }

    private companion object {
        const val REVIEW_DECK_ID = 1L
    }
}
