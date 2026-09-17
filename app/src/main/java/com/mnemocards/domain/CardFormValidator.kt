package com.mnemocards.domain

/**
 * Ошибки валидации полей формы карточки.
 * null в поле — ошибки нет. Текст — 400-аналог (S6–S8, S13).
 */
data class CardFieldErrors(
    val front: String? = null,
    val back: String? = null,
) {
    val isEmpty: Boolean get() = front == null && back == null
}

/**
 * Валидация формы карточки: оба поля обязательны, значения
 * проверяются после trim (пробелы и переводы строк не считаются текстом).
 * Длина текста не ограничена — 200+ символов валидны (S10).
 */
object CardFormValidator {

    const val REQUIRED_MESSAGE = "Заполните поле"

    fun validateCardInput(front: String, back: String): CardFieldErrors =
        CardFieldErrors(
            front = if (front.isBlank()) REQUIRED_MESSAGE else null,
            back = if (back.isBlank()) REQUIRED_MESSAGE else null,
        )
}
