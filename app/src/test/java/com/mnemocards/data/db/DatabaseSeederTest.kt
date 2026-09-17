package com.mnemocards.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.repository.DeckRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты сидинга Room-хранилища (T1/T3, change `add-offline-storage`).
 *
 * Покрывает сценарии spec-bdd:
 * - S1 — первый запуск: автоматически создаётся базовая колода с 5 карточками,
 *   список колод отдаёт название и счётчик «5»;
 * - S2 — повторный запуск: дубликат не создаётся, карточки не плодятся;
 * - S11 — повторная инициализация (пересоздание Activity / повторный вызов
 *   сидинга при существующих данных) не дублирует колоду — данные
 *   перечитываются из Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DatabaseSeederTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var seeder: DatabaseSeeder

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        seeder = DatabaseSeeder(deckDao)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /**
     * S1. GIVEN локальная база пуста (первый запуск).
     * WHEN выполняется сидинг.
     * THEN в БД ровно одна колода с 5 карточками; запрос списка колод
     * через репозиторий отдаёт название колоды и счётчик карточек «5».
     */
    @Test
    fun firstSeed_createsSingleDeckWithFiveCards() = runTest(dispatcher) {
        seeder.seedIfNeeded()

        val deck = deckDao.getDeckById(BASE_DECK_ID)
        assertNotNull("базовая колода должна быть создана", deck)
        assertEquals(BASE_DECK_NAME, deck!!.name)
        assertEquals(5, deckDao.cardCountForDeck(BASE_DECK_ID))

        val repository = DeckRepositoryImpl(deckDao)
        val decks = repository.observeDecks().first().getOrThrow()
        assertEquals(1, decks.size)
        assertEquals(5, decks[0].cardCount)
        assertEquals(BASE_DECK_NAME, decks[0].name)
    }

    /**
     * S2. GIVEN базовая колода уже засеяна, процесс приложения завершён.
     * WHEN приложение запускается снова (повторный вызов сидинга).
     * THEN дубликат базовой колоды не создаётся, карточек по-прежнему 5.
     */
    @Test
    fun repeatedSeed_doesNotDuplicateDeckOrCards() = runTest(dispatcher) {
        seeder.seedIfNeeded()
        seeder.seedIfNeeded()

        assertEquals(5, deckDao.cardCountForDeck(BASE_DECK_ID))
        assertNotNull(deckDao.getDeckById(BASE_DECK_ID))
    }

    /**
     * S11. GIVEN базовая колода существует (экран пересоздан, Activity
     * пересоздана — сидинг вызывается повторно при инициализации).
     * WHEN выполняется многократная повторная инициализация.
     * THEN колода не дублируется: счётчик карточек остаётся 5,
     * данные перечитываются из Room без изменений.
     */
    @Test
    fun activityRecreation_repeatedInit_keepsSingleDeckWithFiveCards() =
        runTest(dispatcher) {
            // «Первый запуск».
            seeder.seedIfNeeded()

            // «Пересоздание Activity» — повторные инициализации контейнера.
            repeat(3) {
                DatabaseSeeder(deckDao).seedIfNeeded()
            }

            assertEquals(5, deckDao.cardCountForDeck(BASE_DECK_ID))

            val repository = DeckRepositoryImpl(deckDao)
            val decks = repository.observeDecks().first().getOrThrow()
            assertEquals(1, decks.size)
            assertEquals(5, decks[0].cardCount)
        }

    private companion object {
        const val BASE_DECK_ID = 1L
        const val BASE_DECK_NAME = "Базовая колода"
    }
}
