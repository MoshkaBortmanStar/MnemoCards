package com.mnemocards.domain.model

/**
 * Результат входа на сервер синхронизации (POST /api/v1/auth/login).
 * При любом исходе, кроме [Success], токены не выдаются.
 */
sealed interface LoginOutcome {
    /**
     * Вход выполнен: токены уже сохранены хранилищем (зашифрованы),
     * экрану достаточно факта успеха.
     */
    data object Success : LoginOutcome

    /** Неверный логин или пароль (HTTP 401). */
    data object InvalidCredentials : LoginOutcome

    /** Невалидный формат тела запроса (HTTP 400), [reason] — причина из тела ответа. */
    data class BadRequest(val reason: String) : LoginOutcome

    /** Ошибка сервера (5xx). */
    data object ServerError : LoginOutcome

    /** Сетевой сбой во время запроса (IOException). Повторная попытка доступна. */
    data object NetworkError : LoginOutcome
}
