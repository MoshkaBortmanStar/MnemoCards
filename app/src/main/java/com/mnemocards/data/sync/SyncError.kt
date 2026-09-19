package com.mnemocards.data.sync

/**
 * Иерархия ошибок фоновой синхронизации (T12, change add-background-sync).
 *
 * Сценарии: S15 (сеть), S16 (5xx), S17 (400), S18 (404),
 * S19 (формат), S13 (требуется вход), S26 (413).
 */
sealed class SyncError(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    /** Соединение не установлено, HTTP-ответа нет (S15). */
    class Network(cause: Throwable?) : SyncError("Нет соединения с сервером", cause)

    /** Синхронизация не настроена — адрес сервера не введён (S14). */
    object NotConfigured : SyncError("Синхронизация не настроена")

    /** Ошибка сервера 5xx (S16). */
    class Server(val code: Int) : SyncError("Ошибка сервера ($code)")

    /** Серверная валидация отклонила запись, HTTP 400 (S17). */
    class Validation(details: String?) :
        SyncError("Сервер отклонил запись: ${details ?: "ошибка валидации"}")

    /** Поток удалён на сервере, HTTP 404 (S18). */
    class StreamDeleted(val streamKey: String) : SyncError("Поток удалён на сервере: $streamKey")

    /** Тело ответа не соответствует схеме (S19). */
    class Format(cause: Throwable?) : SyncError("Невалидный ответ сервера", cause)

    /** Refresh-токен отклонён — требуется вход (S13). */
    object RequiresLogin : SyncError("Требуется вход")

    /** Слишком большой массив изменений, HTTP 413 (S26). */
    object TooManyChanges : SyncError("Слишком много изменений в одном запросе")
}
