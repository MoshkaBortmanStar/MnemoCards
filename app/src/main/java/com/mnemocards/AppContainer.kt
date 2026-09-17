package com.mnemocards

import android.content.Context
import com.mnemocards.data.db.DatabaseSeeder
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.DeckRepositoryImpl
import com.mnemocards.domain.repository.DeckRepository

/**
 * Ручной DI-контейнер приложения: singleton-зависимости создаются
 * один раз на процесс приложения и переживают пересоздание Activity
 * (смену конфигурации), что исключает повторный сидинг (S11).
 *
 * [deckRepository] (T5, change `add-offline-storage`) — единственная
 * точка доступа UI-слоя к офлайн-данным Room: список колод и карточки
 * сессии `cards.review` читаются только через него.
 */
class AppContainer(context: Context) {

    val database: MnemoCardsDatabase =
        MnemoCardsDatabase.build(context.applicationContext)

    val deckRepository: DeckRepository = DeckRepositoryImpl(database.deckDao())

    val databaseSeeder: DatabaseSeeder = DatabaseSeeder(database.deckDao())
}
