package com.mnemocards.domain.scheduler

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Параметризованный тест SM-2 планировщика по спеке add-sm2-scheduling (S1–S12, S15, S23).
 */
class Sm2SchedulerTest {

    private val now = 1_700_000_000_000L // фиксированный «сейчас»

    private fun newState(
        ef: Double = 2.5,
        repetition: Int = 0,
        intervalDays: Int = 0,
        dueDate: Long = now,
    ) = SchedulingState(
        easinessFactor = ef,
        repetitionNumber = repetition,
        intervalDays = intervalDays,
        dueDate = dueDate,
    )

    private val newCard get() = newState()
    private val repeatingCard get() = newState(ef = 2.5, repetition = 2, intervalDays = 6)

    private fun Duration.inDays(): Int = inWholeDays.toInt()

    // --- S1–S4: новая карточка (EF=2.5, №0, интервал 0) ---

    @Test
    fun `S1 new card Easy`() {
        val next = Sm2Scheduler.next(newCard, ReviewGrade.EASY, now)
        assertEquals(2.6, next.easinessFactor, 1e-9)
        assertEquals(1, next.repetitionNumber)
        assertEquals(4, next.intervalDays)
        assertEquals(now + 4.days.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S2 new card Good`() {
        val next = Sm2Scheduler.next(newCard, ReviewGrade.GOOD, now)
        assertEquals(2.5, next.easinessFactor, 1e-9)
        assertEquals(1, next.repetitionNumber)
        assertEquals(1, next.intervalDays)
        assertEquals(now + 1.days.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S3 new card Hard`() {
        val next = Sm2Scheduler.next(newCard, ReviewGrade.HARD, now)
        assertEquals(2.35, next.easinessFactor, 1e-9)
        assertEquals(1, next.repetitionNumber)
        assertEquals(1, next.intervalDays)
        assertEquals(now + 1.days.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S4 new card Forgot`() {
        val next = Sm2Scheduler.next(newCard, ReviewGrade.FORGOT, now)
        assertEquals(2.3, next.easinessFactor, 1e-9)
        assertEquals(0, next.repetitionNumber)
        assertEquals(0, next.intervalDays)
        assertEquals(now + 10.minutes.inWholeMilliseconds, next.dueDate)
    }

    // --- S5–S8: повторяемая карточка (EF=2.5, №2, интервал 6) ---

    @Test
    fun `S5 repeating card Good`() {
        val next = Sm2Scheduler.next(repeatingCard, ReviewGrade.GOOD, now)
        assertEquals(2.5, next.easinessFactor, 1e-9)
        assertEquals(3, next.repetitionNumber)
        assertEquals(15, next.intervalDays) // round(6 × 2.5)
        assertEquals(now + 15.days.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S6 repeating card Easy`() {
        val next = Sm2Scheduler.next(repeatingCard, ReviewGrade.EASY, now)
        assertEquals(2.6, next.easinessFactor, 1e-9)
        assertEquals(3, next.repetitionNumber)
        assertEquals(20, next.intervalDays) // round(6 × 2.6 × 1.3)
        assertEquals(now + 20.days.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S7 repeating card Hard`() {
        val next = Sm2Scheduler.next(repeatingCard, ReviewGrade.HARD, now)
        assertEquals(2.35, next.easinessFactor, 1e-9)
        assertEquals(3, next.repetitionNumber)
        assertEquals(7, next.intervalDays) // max(1, round(6 × 1.2))
        assertEquals(now + 7.days.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S8 repeating card Forgot`() {
        val next = Sm2Scheduler.next(repeatingCard, ReviewGrade.FORGOT, now)
        assertEquals(2.3, next.easinessFactor, 1e-9)
        assertEquals(0, next.repetitionNumber)
        assertEquals(0, next.intervalDays)
        assertEquals(now + 10.minutes.inWholeMilliseconds, next.dueDate)
    }

    // --- S9, S10: клампы EF ---

    @Test
    fun `S9 EF clamp lower bound on Forgot`() {
        val next = Sm2Scheduler.next(newState(ef = 1.3), ReviewGrade.FORGOT, now)
        assertEquals(1.3, next.easinessFactor, 1e-9)
        assertEquals(0, next.repetitionNumber)
        assertEquals(0, next.intervalDays)
        assertEquals(now + 10.minutes.inWholeMilliseconds, next.dueDate)
    }

    @Test
    fun `S10 EF clamp upper bound on Easy`() {
        val state = newState(ef = 2.75, repetition = 2, intervalDays = 6)
        val next = Sm2Scheduler.next(state, ReviewGrade.EASY, now)
        assertEquals(2.8, next.easinessFactor, 1e-9)
        assertEquals(3, next.repetitionNumber)
        assertEquals(22, next.intervalDays) // round(6 × 2.8 × 1.3)
        assertEquals(now + 22.days.inWholeMilliseconds, next.dueDate)
    }

    // --- S11: нормализация повреждённого EF до расчёта ---

    @Test
    fun `S11 corrupted EF is normalized then Easy applied`() {
        val state = newState(ef = 0.9)
        val next = Sm2Scheduler.next(state, ReviewGrade.EASY, now)
        assertEquals(1.4, next.easinessFactor, 1e-9) // 1.3 + 0.10
        assertEquals(1, next.repetitionNumber)
        assertEquals(4, next.intervalDays)
        assertEquals(now + 4.days.inWholeMilliseconds, next.dueDate)
    }

    // --- S12: минимальный интервал graduating-оценок ---

    @Test
    fun `S12 minimum interval of graduating grades is 1 day`() {
        val state = newState(ef = 1.3, repetition = 2, intervalDays = 1)
        val next = Sm2Scheduler.next(state, ReviewGrade.GOOD, now)
        assertEquals(1, next.intervalDays) // round(1 × 1.3) = 1, не 0
        assertEquals(3, next.repetitionNumber)
    }

    // --- S23: карточка №1 + «Хорошо» → фиксированные 6 дней ---

    @Test
    fun `S23 repetition 1 Good gives fixed 6 days`() {
        val state = newState(ef = 2.5, repetition = 1, intervalDays = 1)
        val next = Sm2Scheduler.next(state, ReviewGrade.GOOD, now)
        assertEquals(6, next.intervalDays)
        assertEquals(2, next.repetitionNumber)
        assertEquals(now + 6.days.inWholeMilliseconds, next.dueDate)
    }

    // --- S15: превью совпадает с применяемым результатом ---

    @Test
    fun `S15 preview matches applied result for all grades`() {
        listOf(newCard, repeatingCard, newState(ef = 1.3), newState(ef = 2.75, repetition = 2, intervalDays = 6))
            .forEach { state ->
                val previews = Sm2Scheduler.preview(state, now)
                assertEquals(4, previews.size)
                ReviewGrade.entries.forEach { grade ->
                    val applied = Sm2Scheduler.next(state, grade, now)
                    val expectedDuration = when (grade) {
                        ReviewGrade.FORGOT -> 10.minutes
                        else -> applied.intervalDays.days
                    }
                    assertEquals(
                        "grade=$grade ef=${state.easinessFactor} rep=${state.repetitionNumber}",
                        expectedDuration,
                        previews.getValue(grade),
                    )
                }
            }
    }

    // --- S14 (числа): превью повторяемой карточки ---

    @Test
    fun `S14 preview values for repeating card`() {
        val previews = Sm2Scheduler.preview(repeatingCard, now)
        assertEquals(10.minutes, previews.getValue(ReviewGrade.FORGOT))
        assertEquals(7.days, previews.getValue(ReviewGrade.HARD))
        assertEquals(15.days, previews.getValue(ReviewGrade.GOOD))
        assertEquals(20.days, previews.getValue(ReviewGrade.EASY))
    }
}
