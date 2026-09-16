package com.mnemocards.domain.model

/**
 * Оценка ответа пользователя: «Забыл», «Трудно», «Хорошо», «Легко».
 *
 * В рамках фичи `add-review-screen` все оценки эквивалентны:
 * пересчёт интервалов повторения (SRS) вне скоупа (S6).
 */
enum class Grade {
    FORGOT,
    HARD,
    GOOD,
    EASY,
}
