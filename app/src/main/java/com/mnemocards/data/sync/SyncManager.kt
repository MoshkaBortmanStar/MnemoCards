package com.mnemocards.data.sync

import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.db.sync.LastSyncResultEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * Статус фоновой синхронизации для UI (change-009, T15/T16).
 * Отображается только на экране настроек (S21).
 */
sealed class SyncStatus {
    /** Синхронизация не настроена либо ещё не запускалась (S14). */
    object Idle : SyncStatus()

    /** Сессия выполняется (S2, S23). */
    object Running : SyncStatus()

    /** Последняя сессия завершилась успешно. */
    object Success : SyncStatus()

    /** Последняя сессия завершилась ошибкой (S15–S19, S26). */
    data class Error(val error: SyncError) : SyncStatus()

    /** Refresh-токен отклонён — требуется вход (S13). */
    object RequiresLogin : SyncStatus()
}

/**
 * Сессия синхронизации, наблюдаемая UI: статус и итог последней сессии,
 * команда запуска. [SyncManager] — единственная реализация; интерфейс
 * нужен как шов для тестов ViewModel.
 */
interface SyncSession {
    val status: StateFlow<SyncStatus>
    val lastResult: Flow<LastSyncResultEntity?>
    fun requestSync()
}

/**
 * Запуск одной сессии: null — успех, иначе ошибка. Реализация —
 * [SyncEngine.syncNow]; шов для детерминированных тестов менеджера.
 */
fun interface SessionRunner {
    suspend fun run(): SyncError?
}

/**
 * Оркестратор фоновых сессий синхронизации (change-009, T16).
 *
 * Single-flight (S23): повторный [requestSync] при выполняющейся сессии
 * не создаёт параллельную — текущая продолжает работу, статус остаётся
 * «выполняется», по завершении фиксируется один итоговый результат.
 *
 * Признак «синхронизация настроена» — наличие сохранённых токенов (S14):
 * без них сетевые вызовы не выполняются вовсе, локальный функционал
 * работает в полном объёме (S20).
 *
 * Сессия запускается на переданном [scope] (application-scope) с
 * диспетчером IO — UI не блокируется (S1, S2, S22).
 */
class SyncManager(
    private val runner: SessionRunner,
    private val db: MnemoCardsDatabase,
    /** Наличие токенов — признак настроенной синхронизации (S14). */
    private val isConfigured: suspend () -> Boolean,
    private val scope: CoroutineScope,
) : SyncSession {

    private val mutex = Mutex()

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    override val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Итог последней сессии из Room (S2) — наблюдается экраном настроек. */
    override val lastResult: Flow<LastSyncResultEntity?> =
        db.syncStateDao().observeLastSyncResult()

    /**
     * Публичная команда запуска сессии (S1 — автозапуск, S2 — кнопка
     * на настройках). Неблокирующая: сессия выполняется в фоне на
     * диспетчере [scope] (в проде — Dispatchers.IO).
     */
    override fun requestSync() {
        scope.launch {
            runOnce()
        }
    }

    private suspend fun runOnce() {
        // S14: синхронизация не настроена — сети не будет, статус не меняется.
        if (!isConfigured()) return
        // S23: single-flight — параллельная сессия не создаётся.
        if (!mutex.tryLock()) return
        try {
            _status.value = SyncStatus.Running
            val error = runner.run()
            _status.value = when (error) {
                null -> SyncStatus.Success
                is SyncError.RequiresLogin -> SyncStatus.RequiresLogin
                else -> SyncStatus.Error(error)
            }
        } finally {
            mutex.unlock()
        }
    }
}
