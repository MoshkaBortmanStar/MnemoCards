package com.mnemocards.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.data.db.CardEntity
import com.mnemocards.data.db.DatabaseSeeder
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.DeckEntity
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.domain.repository.DeckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты репозитория колод над Room (T4/T5, change `add-offline-storage`).
 *
 * Дополнительно покрывает сценарии T16:
 * - S10 — полное отсутствие сети не влияет на работу: репозиторий
 *   работает только поверх Room, сетевые вызовы не выполняются
 *   (Robolectric-окружение без сети, Retrofit в графе зависимостей
 *   репозитория отсутствует — см. конструктор [DeckRepositoryImpl]);
 * - S11 — повторная инициализация не дублирует данные;
 * - S12 — после полной сессии из 5 карточек счётчик остаётся равным 5.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeckRepositoryImplTest {

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var repository: DeckRepository

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        repository = DeckRepositoryImpl(deckDao)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    /**
     * S10/S3. GIVEN базовая колода засеяна в Room.
     * WHEN `observeDecks()` собирается без какой-либо сети
     * (Robolectric не предоставляет сетевого доступа).
     * THEN список колод эмитится из Room: название + счётчик «5»;
     * сетевых вызовов фича не выполняет, сообщений о сети нет.
     */
    @Test
    fun observeDecks_worksFullyOffline_emitsDeckWithCount() = runTest(dispatcher) {
        DatabaseSeeder(deckDao).seedIfNeeded()

        val decks = repository.observeDecks().first().getOrThrow()

        assertEquals(1, decks.size)
        assertEquals("Базовая колода", decks[0].name)
        assertEquals(5, decks[0].cardCount)
    }

    /**
     * S5/S10. GIVEN колода с 5 карточками и вторая колода с другими данными.
     * WHEN `observeCards(deckId)` собирается для выбранной колоды.
     * THEN возвращаются только карточки выбранной колоды, офлайн.
     */
    @Test
    fun observeCards_returnsOnlyCardsOfSelectedDeck_offline() = runTest(dispatcher) {
        DatabaseSeeder(deckDao).seedIfNeeded()
        val otherDeckId = deckDao.insertDeckWithCards(
            DeckEntity(id = 0L, name = "Другая"),
            listOf(CardEntity(id = 0L, deckId = 0L, front = "one", back = "один")),
        )

        val baseCards = repository.observeCards(BASE_DECK_ID).first().getOrThrow()
        val otherCards = repository.observeCards(otherDeckId).first().getOrThrow()

        assertEquals(5, baseCards.size)
        assertEquals(1, otherCards.size)
        assertEquals("one", otherCards[0].front)
    }

    /**
     * S11. GIVEN данные уже существуют.
     * WHEN выполняется повторная инициализация (повторный сидинг
     * и пересоздание репозитория над той же БД).
     * THEN колода одна, карточек по-прежнему 5 — перечитано из Room.
     */
    @Test
    fun repeatedInit_doesNotDuplicateData() = runTest(dispatcher) {
        DatabaseSeeder(deckDao).seedIfNeeded()
        DatabaseSeeder(deckDao).seedIfNeeded()
        val recreatedRepository = DeckRepositoryImpl(deckDao)

        val decks = recreatedRepository.observeDecks().first().getOrThrow()

        assertEquals(1, decks.size)
        assertEquals(5, decks[0].cardCount)
        assertEquals(5, deckDao.cardCountForDeck(BASE_DECK_ID))
    }

    /**
     * S12. GIVEN пользователь полностью завершил сессию из 5 карточек,
     * оценив каждый ответ (сессия не выполняет мутаций).
     * WHEN выполняется запрос счётчика и списка колод.
     * THEN счётчик карточек колоды по-прежнему равен 5; отображаемое
     * количество соответствует данным БД.
     */
    @Test
    fun afterFullSession_cardCountStaysFive() = runTest(dispatcher) {
        DatabaseSeeder(deckDao).seedIfNeeded()

        // Полная сессия: чтение карточек, никаких мутаций через репозиторий.
        val cards = repository.observeCards(BASE_DECK_ID).first().getOrThrow()
        assertEquals(5, cards.size)

        val decks = repository.observeDecks().first().getOrThrow()

        assertEquals(5, deckDao.cardCountForDeck(BASE_DECK_ID))
        assertEquals(5, decks.first { it.id == BASE_DECK_ID }.cardCount)
    }

    /**
     * S9 (аналог 500). GIVEN при чтении из БД возникает исключение
     * (закрытая БД).
     * WHEN `observeDecks()` собирается.
     * THEN наружу отдаётся ошибочный Result, а не необработанный крах.
     */
    @Test
    fun closedDatabase_emitsFailureResult_withoutCrash() = runTest(dispatcher) {
        db.close()

        val result = repository.observeDecks().first()

        assertTrue(result.isFailure)
    }

    private companion object {
        const val BASE_DECK_ID = 1L
    }
}
