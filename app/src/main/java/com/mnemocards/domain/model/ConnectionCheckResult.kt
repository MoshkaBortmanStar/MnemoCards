package com.mnemocards.domain.model

/**
 * Результат проверки соединения с сервером синхронизации (GET /api/v1/health).
 * Состояние подключения пользователя не меняет: проверка не выполняет вход.
 */
sealed interface ConnectionCheckResult {
    /** Сервер ответил 200 на эндпоинт доступности. */
    data object ServerResponds : ConnectionCheckResult

    /** По этому адресу нет сервера синхронизации (HTTP 404). */
    data object NoSyncServer : ConnectionCheckResult

    /** Ошибка сервера (5xx). */
    data object ServerError : ConnectionCheckResult

    /** Адрес недоступен: домен не резолвится, таймаут соединения и т.п. */
    data object Unreachable : ConnectionCheckResult

    /** Сервер отвечает по HTTPS, но сертификат не принят (S7). Обход проверки
     * сертификата не выполняется. */
    data object CertificateRejected : ConnectionCheckResult
}
