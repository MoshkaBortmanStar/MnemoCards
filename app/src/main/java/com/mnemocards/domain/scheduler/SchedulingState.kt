package com.mnemocards.domain.scheduler

/**
 * Состояние планирования карточки по SM-2.
 *
 * @param easinessFactor коэффициент лёгкости, всегда в диапазоне [1.3, 2.8].
 * @param repetitionNumber номер успешного повторения (0 — новая карточка).
 * @param intervalDays интервал до следующего показа в целых днях
 *   (0 — только у оценки «Забыл»; 10-минутный сдвиг живёт в [dueDate]).
 * @param dueDate момент следующего показа, epochMillis.
 */
data class SchedulingState(
    val easinessFactor: Double,
    val repetitionNumber: Int,
    val intervalDays: Int,
    val dueDate: Long,
) {
    companion object {
        const val MIN_EF = 1.3
        const val MAX_EF = 2.8
        const val DEFAULT_EF = 2.5

        /** Параметры новой карточки: EF=2.5, №0, интервал 0, due=«сейчас». */
        fun fresh(now: Long): SchedulingState = SchedulingState(
            easinessFactor = DEFAULT_EF,
            repetitionNumber = 0,
            intervalDays = 0,
            dueDate = now,
        )
    }
}
