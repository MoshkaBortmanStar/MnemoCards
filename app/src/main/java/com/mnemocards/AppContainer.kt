package com.mnemocards

import android.content.Context
import com.mnemocards.data.db.MnemoCardsDatabase

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
}
