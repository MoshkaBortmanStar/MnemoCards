package com.mnemocards.ui.review

import kotlin.time.Duration

/**
 * Форматирование интервала повторения для подписи на кнопке оценки (S13).
 *
 * Дни склоняются по правилам русской плюрализации (день / дня / дней),
 * минуты сокращаются до «N мин».
 */
fun formatInterval(duration: Duration): String {
    val days = duration.inWholeDays
    return if (days >= 1) {
        "$days ${pluralizeDays(days)}"
    } else {
        "${duration.inWholeMinutes} мин"
    }
}

private fun pluralizeDays(n: Long): String = when {
    n % 10 == 1L && n % 100 != 11L -> "день"
    n % 10 in 2L..4L && n % 100 !in 12L..14L -> "дня"
    else -> "дней"
}
