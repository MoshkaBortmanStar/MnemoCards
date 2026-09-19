package com.mnemocards

import android.app.Application
import com.mnemocards.data.db.DatabaseSeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Точка входа приложения: инициализирует DI-контейнер и запускает
 * сидинг базовой колоды в фоновой корутине (S1).
 *
 * change-009: автозапуск фоновой синхронизации при открытии приложения
 * (S1); без сохранённых токенов сессия не выполняется (S14).
 */
class MnemoCardsApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        applicationScope.launch {
            DatabaseSeeder.seedIfEmpty(container.database)
        }
        container.syncManager.requestSync()
    }
}
