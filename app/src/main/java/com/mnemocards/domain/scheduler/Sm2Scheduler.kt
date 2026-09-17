package com.mnemocards.domain.scheduler

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * SM-2 планировщик (change add-sm2-scheduling).
 *
 * Единая функция расчёта используется и для подписей на кнопках ([preview]),
 * и для применения оценки ([next]) — расхождение подписи и результата исключено.
 */
object Sm2Scheduler {

    /** Сдвиг для «Забыл»: карточка возвращается через 10 минут. */
    private val FORGOT_DELAY = 10.minutes

    /**
     * Применяет оценку к состоянию карточки.
     *
     * @param now текущий момент, epochMillis — база для нового dueDate.
     */
    fun next(state: SchedulingState, grade: ReviewGrade, now: Long): SchedulingState {
        // Повреждённый EF (ниже минимума) нормализуется к границе до расчёта.
        val ef = state.easinessFactor.coerceIn(SchedulingState.MIN_EF, SchedulingState.MAX_EF)

        return when (grade) {
            ReviewGrade.FORGOT -> {
                val nextEf = max(SchedulingState.MIN_EF, ef - 0.20)
                // Прогресс сброшен; 10-минутный сдвиг живёт только в dueDate.
                SchedulingState(
                    easinessFactor = nextEf,
                    repetitionNumber = 0,
                    intervalDays = 0,
                    dueDate = now + FORGOT_DELAY.inWholeMilliseconds,
                )
            }

            ReviewGrade.HARD -> {
                val nextEf = max(SchedulingState.MIN_EF, ef - 0.15)
                val interval = if (state.repetitionNumber == 0) {
                    1
                } else {
                    max(1, (state.intervalDays * 1.2).roundToInt())
                }
                SchedulingState(
                    easinessFactor = nextEf,
                    repetitionNumber = state.repetitionNumber + 1,
                    intervalDays = interval,
                    dueDate = now + interval.days.inWholeMilliseconds,
                )
            }

            ReviewGrade.GOOD -> {
                val interval = when (state.repetitionNumber) {
                    0 -> 1
                    1 -> 6
                    else -> max(1, (state.intervalDays * ef).roundToInt())
                }
                SchedulingState(
                    easinessFactor = ef,
                    repetitionNumber = state.repetitionNumber + 1,
                    intervalDays = interval,
                    dueDate = now + interval.days.inWholeMilliseconds,
                )
            }

            ReviewGrade.EASY -> {
                val nextEf = min(SchedulingState.MAX_EF, ef + 0.10)
                val interval = if (state.repetitionNumber == 0) {
                    4
                } else {
                    max(1, (state.intervalDays * nextEf * 1.3).roundToInt())
                }
                SchedulingState(
                    easinessFactor = nextEf,
                    repetitionNumber = state.repetitionNumber + 1,
                    intervalDays = interval,
                    dueDate = now + interval.days.inWholeMilliseconds,
                )
            }
        }
    }

    /**
     * Превью интервалов для четырёх кнопок оценки. Использует тот же расчёт,
     * что и [next]: интервал каждой кнопки — это интервал, который будет применён.
     */
    fun preview(state: SchedulingState, now: Long): Map<ReviewGrade, Duration> =
        ReviewGrade.entries.associateWith { grade ->
            val applied = next(state, grade, now)
            if (grade == ReviewGrade.FORGOT) FORGOT_DELAY else applied.intervalDays.days
        }
}
