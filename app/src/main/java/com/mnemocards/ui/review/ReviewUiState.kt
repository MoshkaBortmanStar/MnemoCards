package com.mnemocards.ui.review

/**
 * Состояния экрана тренировки колоды (T11, change `add-offline-storage`).
 *
 * Логика переворота, четырёх эквивалентных оценок и терминального экрана
 * «на сегодня всё» сохранена из change `add-review-screen`; изменился только
 * источник данных — карточки читаются из Room через `DeckRepository`
 * по идентификатору выбранной колоды.
 */
sealed interface ReviewUiState {

    /** Карточки читаются из Room (S4/S5). */
    data object Loading : ReviewUiState

    /**
     * Колода/карточки отсутствуют в БД к моменту открытия тренировки (S8):
     * экран не открывается на пустом наборе, выполняется возврат к списку.
     */
    data object NotFound : ReviewUiState

    /**
     * Активная карточка сессии.
     *
     * @param front слово на лицевой стороне;
     * @param back перевод на оборотной стороне;
     * @param flipped перевёрнута ли карточка;
     * @param position номер текущей карточки (с единицы);
     * @param total всего карточек в сессии.
     */
    data class Session(
        val front: String,
        val back: String,
        val flipped: Boolean,
        val position: Int,
        val total: Int,
    ) : ReviewUiState

    /** Все карточки колоды оценены — «на сегодня всё» (S12). */
    data object Finished : ReviewUiState
}

/**
 * Оценка ответа. Четыре оценки эквивалентны по влиянию на переход
 * к следующей карточке (change `add-review-screen`); влияние на SRS —
 * предмет `srs.scheduling`.
 */
enum class ReviewGrade {
    FORGOT,
    HARD,
    GOOD,
    EASY,
}
