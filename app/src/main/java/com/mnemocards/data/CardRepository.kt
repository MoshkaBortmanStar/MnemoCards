package com.mnemocards.data

import com.mnemocards.data.db.CardDao
import com.mnemocards.data.db.CardEntity
import com.mnemocards.domain.model.Card
import com.mnemocards.domain.scheduler.ReviewGrade
import com.mnemocards.domain.scheduler.SchedulingState
import com.mnemocards.domain.scheduler.Sm2Scheduler

/**
 * Репозиторий карточек поверх Room-DAO и планировщика SM-2
 * (change add-sm2-scheduling). Маппинг entity ↔ domain живёт здесь —
 * сущности наружу из `data` не выходят.
 */
interface CardRepository {

    /** Назревшие карточки колоды (dueDate <= now) с параметрами планирования (S16). */
    suspend fun dueCards(deckId: String, now: Long): List<Card>

    /** Применяет оценку [grade] и записывает результат SM-2 в Room (S1–S8). */
    suspend fun applyGrade(card: Card, grade: ReviewGrade, now: Long)

    /** Существование колоды — состояние NotFound при устаревшем id (S32). */
    suspend fun deckExists(deckId: String): Boolean
}

class RoomCardRepository(
    private val cardDao: CardDao,
) : CardRepository {

    override suspend fun dueCards(deckId: String, now: Long): List<Card> =
        cardDao.getDueCards(deckId, now).map { it.toDomain() }

    override suspend fun applyGrade(card: Card, grade: ReviewGrade, now: Long) {
        val next = Sm2Scheduler.next(card.toSchedulingState(), grade, now)
        cardDao.updateScheduling(
            cardId = card.id,
            easinessFactor = next.easinessFactor,
            repetitionNumber = next.repetitionNumber,
            intervalDays = next.intervalDays,
            dueDate = next.dueDate,
        )
    }

    override suspend fun deckExists(deckId: String): Boolean =
        cardDao.deckExists(deckId)

    private fun CardEntity.toDomain(): Card = Card(
        id = id,
        deckId = deckId,
        front = front,
        back = back,
        easinessFactor = easinessFactor,
        repetitionNumber = repetitionNumber,
        intervalDays = intervalDays,
        dueDate = dueDate,
    )

    private fun Card.toSchedulingState(): SchedulingState = SchedulingState(
        easinessFactor = easinessFactor,
        repetitionNumber = repetitionNumber,
        intervalDays = intervalDays,
        dueDate = dueDate,
    )
}
