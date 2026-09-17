package com.mnemocards.ui.review

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.mnemocards.data.db.DatabaseSeeder
import com.mnemocards.data.db.DeckDao
import com.mnemocards.data.db.MnemoCardsDatabase
import com.mnemocards.data.repository.DeckRepositoryImpl
import com.mnemocards.domain.model.Deck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты ViewModel сессии повторения на данных Room
 * (T10/T11, change `add-offline-storage`).
 *
 * Дополнительно покрывает сценарии T16:
 * - S10 — сессия полностью офлайн: карточки читаются из Room,
 *   сетевые вызовы не выполняются;
 * - S12 — полный проход сессии не изменяет данные репозитория:
 *   счётчик карточек колоды остаётся равным 5.
 *
 * Загрузка карточек идёт через Room на реальном IO-диспатчере, поэтому
 * тесты ждут целевое состояние хелпером [awaitState] (реальное время).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReviewViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    private lateinit var db: MnemoCardsDatabase
    private lateinit var deckDao: DeckDao
    private lateinit var repository: DeckRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MnemoCardsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        deckDao = db.deckDao()
        repository = DeckRepositoryImpl(deckDao)
        kotlinx.coroutines.runBlocking { DatabaseSeeder(deckDao).seedIfNeeded() }
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel(deckId: Long = 1L): ReviewViewModel =
        ReviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("deckId" to deckId)),
            repository = repository,
        )

    /**
     * S5/S10. GIVEN в Room колода с 5 карточками.
     * WHEN открывается тренировка (без сети).
     * THEN сессия загружает все карточки из Room по идентификатору
     * выбранной колоды; показана лицевая сторона первой карточки.
     */
    @Test
    fun session_loadsCardsFromRoom_forSelectedDeck() = runTest(dispatcher) {
        val vm = viewModel()

        val state = awaitState(vm) { it is ReviewUiState.Session } as ReviewUiState.Session
        assertEquals(5, state.total)
        assertEquals("cat", state.front)
    }

    /**
     * S12. GIVEN колода с 5 карточками.
     * WHEN пользователь полностью проходит сессию (перевороты,
     * четыре оценки, терминальный экран).
     * THEN данные репозитория не изменяются: счётчик карточек колоды
     * в БД по-прежнему равен 5, список колод показывает «5».
     */
    @Test
    fun fullSession_doesNotChangeRepositoryData() = runTest(dispatcher) {
        val vm = viewModel()
        awaitState(vm) { it is ReviewUiState.Session }

        repeat(5) {
            vm.onFlip()
            vm.onGrade(ReviewGrade.entries[it % ReviewGrade.entries.size])
        }

        assertEquals(ReviewUiState.Finished, vm.uiState.value)
        assertEquals(5, deckDao.cardCountForDeck(1L))

        val decks = repository.observeDecks().firstValue()
        assertEquals(5, decks.first { it.id == 1L }.cardCount)
    }

    /**
     * S6/S12. GIVEN сессия завершена («на сегодня всё»).
     * WHEN выполняется «назад».
     * THEN эмитится событие навигации, данные не тронуты.
     */
    @Test
    fun onBack_fromFinished_emitsNavigateBack() = runTest(dispatcher) {
        val vm = viewModel()
        awaitState(vm) { it is ReviewUiState.Session }
        repeat(5) {
            vm.onFlip()
            vm.onGrade(ReviewGrade.GOOD)
        }

        vm.navigateBack.test {
            vm.onBack()
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(5, deckDao.cardCountForDeck(1L))
    }

    /**
     * S8 (аналог 404). GIVEN колоды с данным идентификатором в БД нет.
     * WHEN открывается тренировка.
     * THEN состояние NotFound и событие возврата к списку.
     */
    @Test
    fun missingDeck_resultsInNotFoundAndNavigateBack() = runTest(dispatcher) {
        val vm = viewModel(deckId = 999L)

        assertEquals(
            ReviewUiState.NotFound,
            awaitState(vm) { it is ReviewUiState.NotFound },
        )
        vm.navigateBack.test {
            assertEquals(Unit, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * Ждёт состояние, удовлетворяющее предикату, в реальном времени:
     * загрузка карточек выполняется Room на IO-диспатчере, а не на
     * виртуальных часах [runTest].
     */
    private suspend fun awaitState(
        vm: ReviewViewModel,
        predicate: (ReviewUiState) -> Boolean,
    ): ReviewUiState = withContext(Dispatchers.Default) {
        val deadline = System.currentTimeMillis() + AWAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            vm.uiState.value.let { state ->
                if (predicate(state)) return@withContext state
            }
            Thread.sleep(50)
        }
        error("состояние не достигнуто за ${AWAIT_TIMEOUT_MS} мс: ${vm.uiState.value}")
    }

    private companion object {
        const val AWAIT_TIMEOUT_MS = 10_000L
    }
}

/** Хелп: первое успешное значение Flow<Result<List<Deck>>>. */
private suspend fun <T> Flow<Result<List<T>>>.firstValue(): List<T> =
    firstOrNull { it.isSuccess }?.getOrThrow() ?: error("нет успешного значения")
