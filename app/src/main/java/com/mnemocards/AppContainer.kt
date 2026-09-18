package com.mnemocards

import android.content.Context
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.file.CsvFileGateway
import com.mnemocards.data.file.SafCsvFileGateway
import com.mnemocards.data.sync.SyncFlagDataStore

/**
 * Ручной DI-контейнер приложения: singleton-зависимости создаются
 * один раз на процесс приложения и переживают пересоздание Activity
 * (смену конфигурации), что исключает повторный сидинг (S11).
 *
 * Room — единый источник истины: UI-слой читает колоды и карточки
 * через DAO и [com.mnemocards.data.repository.RoomCardRepository].
 *
 * change-007: добавлен [syncFlagDataStore] — персистентный флаг
 * «синхронизация включена» (DataStore, по умолчанию выключен, S14);
 * он определяет режим удаления: физический или мягкий (S17–S20).
 * UI-переключателя в этой фиче нет — флагом будет управлять будущий
 * экран настроек сервера (S15).
 */
class AppContainer(context: Context) {

    val database: MnemoCardsDatabase =
        MnemoCardsDatabase.getInstance(context.applicationContext)

    /**
     * Файловый шлюз CSV (change-006): доступ к документам только через
     * ContentResolver/SAF, без разрешений хранилища (S24).
     */
    val csvFileGateway: CsvFileGateway =
        SafCsvFileGateway(context.applicationContext.contentResolver)

    /** Единственный на процесс экземпляр хранилища флага синхронизации (S14–S16). */
    val syncFlagDataStore: SyncFlagDataStore =
        SyncFlagDataStore.fromContext(context.applicationContext)
}
