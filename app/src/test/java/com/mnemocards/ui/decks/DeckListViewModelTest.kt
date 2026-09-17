package com.mnemocards.ui.decks

import app.cash.turbine.test
import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.model.ReviewCard
import com.mnemocards.domain.repository.DeckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Тесты [DeckListViewModel] — состояний стартового экрана (T6/T15,
 * change `add-offline-storage`).
 *
 * Покрывает сценарии spec-bdd:
 * - S3 — список колод «название + счётчик карточек» из данных Room;
 * - S7 — пустая БД → защитное пустое состояние;
 * - S9 (аналог 500) — ошибка инициализации/чтения БД → состояние ошибки
 *   с возможностью повторить; пустые или частичные данные не выдаются
 *   за валидные; повтор после восстановления → `Content`.
 *
 * `uiState` — `stateIn` на Unconfined-диспатчере: к моменту подписки
 * коллектора upstream уже собран, Turbine сразу видит целевое состояние.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeckListViewModelTest {

    private val decks = listOf(
        Deck(id = 1L, name = "Базовая колода", cardCount = 5),
        Deck(id = 2L, name = "Дополнительная", cardCount = 2),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * S3. GIVEN в БД хранятся колоды с карточками.
     * WHEN пользователь открывает стартовый экран.
     * THEN состояние экрана — `Content` со списком колод
     * (id, название, счётчик карточек).
     */
    @Test
    fun decksPresent_emitsContentWithDecksAndCounts() = runTest {
        val viewModel = DeckListViewModel(
            FakeDeckRepository(MutableStateFlow(Result.success(decks))),
        )

        viewModel.uiState.test {
            val state = awaitItem()
            assertTrue(state is DeckListUiState.Content)
            assertEquals(decks, (state as DeckListUiState.Content).decks)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * S7. GIVEN БД инициализирована, но не содержит ни одной колоды
     * (сидинг по какой-то причине не выполнился).
     * WHEN пользователь открывает стартовый экран.
     * THEN отображается пустое состояние — эмитится `Empty`,
     * неинформативный пустой экран исключён.
     */
    @Test
    fun emptyDatabase_emitsEmptyState() = runTest {
        val viewModel = DeckListViewModel(
            FakeDeckRepository(flowOf(Result.success(emptyList()))),
        )

        viewModel.uiState.test {
            assertEquals(DeckListUiState.Empty, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * S9 (аналог 500). GIVEN при открытии базы Room возникает исключение
     * (повреждён файл БД, ошибка миграции схемы) — репозиторий отдаёт
     * ошибочный результат.
     * WHEN пользователь открывает приложение.
     * THEN стартовый экран переходит в состояние `Error`: пустые или
     * частичные данные не выдаются за валидные (Content/Empty не эмитятся).
     */
    @Test
    fun databaseFailure_emitsErrorState_notFakeContent() = runTest {
        val viewModel = DeckListViewModel(
            FakeDeckRepository(
                flowOf(Result.failure(IllegalStateException("повреждён файл БД"))),
            ),
        )

        viewModel.uiState.test {
            assertTrue(awaitItem() is DeckListUiState.Error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * S9 (аналог 500). GIVEN стартовый экран находится в состоянии ошибки.
     * WHEN ошибка БД устранена и пользователь нажимает «Повторить».
     * THEN повторная подписка на Flow Room отдаёт данные — состояние
     * переходит в `Content` с валидным списком колод.
     */
    @Test
    fun retry_afterFailureRecovery_emitsContent() = runTest {
        val results = MutableStateFlow<Result<List<Deck>>>(
            Result.failure(IllegalStateException("ошибка миграции схемы")),
        )
        val viewModel = DeckListViewModel(FakeDeckRepository(results))

        viewModel.uiState.test {
            assertTrue(awaitItem() is DeckListUiState.Error)

            // БД восстановлена — повторная попытка.
            results.value = Result.success(decks)
            viewModel.retry()

            val state = awaitItem()
            assertTrue(state is DeckListUiState.Content)
            assertEquals(decks, (state as DeckListUiState.Content).decks)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

/**
 * Фейковый [DeckRepository] с переключаемым результатом `observeDecks`:
 * позволяет смоделировать ошибку БД и последующее восстановление (S9).
 */
private class FakeDeckRepository(
    private val decksResult: Flow<Result<List<Deck>>>,
) : DeckRepository {

    override fun observeDecks(): Flow<Result<List<Deck>>> = decksResult

    override fun observeCards(deckId: Long): Flow<Result<List<ReviewCard>>> =
        flowOf(Result.success(emptyList()))
}
