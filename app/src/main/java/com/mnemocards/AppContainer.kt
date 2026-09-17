package com.mnemocards

import android.content.Context
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.file.CsvFileGateway
import com.mnemocards.data.file.SafCsvFileGateway

/**
 * Ручной DI-контейнер приложения: singleton-зависимости создаются
 * один раз на процесс приложения и переживают пересоздание Activity
 * (смену конфигурации), что исключает повторный сидинг (S11).
 *
 * Room — единый источник истины: UI-слой читает колоды и карточки
 * через DAO и [com.mnemocards.data.repository.RoomCardRepository].
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
}
