package com.mnemocards.ui.review

import com.mnemocards.domain.model.Card
import com.mnemocards.domain.scheduler.ReviewGrade
import kotlin.time.Duration

/**
 * Состояние экрана тренировки (change add-sm2-scheduling).
 *
 * Ошибка загрузки — аналог 500 (S31), NotFound — аналог 404 (S32):
 * оба выражены состояниями UI, краша нет.
 */
sealed interface ReviewUiState {

    /** Сессия загружается из Room. */
    data object Loading : ReviewUiState

    /** Колода не найдена (устаревший идентификатор навигации, S32). */
    data object NotFound : ReviewUiState

    /** Ошибка чтения БД при формировании сессии (S31). */
    data object Error : ReviewUiState

    /**
     * Активная карточка сессии.
     *
     * @property previews прогнозируемые интервалы четырёх кнопок оценки
     * (миллисекунды до due, S14, S15) — вычисляются той же функцией,
     * что и применяет оценку.
     * @property saveError ошибка записи в БД при оценке (S30): карточка
     * остаётся в сессии неоценённой.
     */
    data class Session(
        val card: Card,
        val isFlipped: Boolean,
        val previews: Map<ReviewGrade, Duration>,
        val saveError: Boolean = false,
    ) : ReviewUiState

    /** Очередь пуста — «на сегодня всё» (S17, S18, S20). */
    data object Done : ReviewUiState
}
