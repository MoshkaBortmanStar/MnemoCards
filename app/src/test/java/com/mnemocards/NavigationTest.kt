package com.mnemocards

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.testing.TestNavHostController
import androidx.test.core.app.ApplicationProvider
import com.mnemocards.domain.model.Deck
import com.mnemocards.domain.model.ReviewCard
import com.mnemocards.domain.repository.DeckRepository
import com.mnemocards.ui.MnemoCardsNavHost
import com.mnemocards.ui.decks.DeckListViewModel
import com.mnemocards.ui.review.ReviewGrade
import com.mnemocards.ui.review.ReviewViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Навигационные тесты приложения (T12/T14, change `add-offline-storage`).
 *
 * Покрывает сценарии spec-bdd:
 * - S3 — стартовый экран со списком колод (название + счётчик карточек);
 * - S4 — тап по колоде открывает маршрут `review/{deckId}`
 *   с корректным аргументом;
 * - S6 — системный «назад» (в том числе с терминального экрана
 *   «на сегодня всё») возвращает к списку колод;
 * - S8 — тап по колоде, отсутствующей в БД к моменту тапа (аналог 404):
 *   тренировка не открывается на пустом наборе, пользователь остаётся
 *   на списке, крах исключён;
 * - S12 — после возврата список отрисован с теми же колодами и счётчиками.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController
    private lateinit var repository: NavigationFakeRepository

    private val decks = listOf(
        Deck(id = 1L, name = "Базовая колода", cardCount = 5),
        Deck(id = 2L, name = "Дополнительная", cardCount = 2),
    )

    @Before
    fun setUp() {
        navController = TestNavHostController(ApplicationProvider.getApplicationContext())
        // NavHost-compose требует ComposeNavigator в провайдере TestNavHostController.
        navController.navigatorProvider.addNavigator(androidx.navigation.compose.ComposeNavigator())
        repository = NavigationFakeRepository(decks)

        composeRule.setContent {
            MnemoCardsNavHost(
                navController = navController,
                deckListViewModel = DeckListViewModel(repository),
                reviewViewModelFactory = { deckId ->
                    ReviewViewModel(
                        savedStateHandle = SavedStateHandle(mapOf("deckId" to deckId)),
                        repository = repository,
                    )
                },
            )
        }
    }

    /**
     * S3. GIVEN приложение запущено и в БД есть колоды.
     * WHEN пользователь оказывается на стартовом экране.
     * THEN текущим назначением является маршрут списка колод `decks`,
     * список отрисован с названиями и счётчиками карточек.
     */
    @Test
    fun appOpens_onDeckListRoute_withDecksDisplayed() {
        composeRule.onNodeWithText("Базовая колода").assertExists()
        composeRule.onNodeWithText("5 карточек").assertExists()

        assertEquals("decks", navController.currentDestination?.route)
    }

    /**
     * S4. GIVEN на стартовом экране отображается список колод.
     * WHEN пользователь тапает по колоде.
     * THEN навигация ведёт на маршрут `review/{deckId}` с корректным
     * аргументом; показана лицевая сторона первой карточки этой колоды.
     */
    @Test
    fun tapOnDeck_navigatesToReviewRouteWithDeckIdArgument() {
        composeRule.onNodeWithText("Базовая колода").performClick()

        composeRule.runOnIdle {
            assertEquals("review/{deckId}", navController.currentDestination?.route)
            assertEquals(1L, navController.currentBackStackEntry?.arguments?.getLong("deckId"))
        }

        composeRule.onNodeWithText("cat").assertExists()
    }

    /**
     * S6. GIVEN пользователь находится на экране тренировки колоды.
     * WHEN выполняется системный «назад».
     * THEN выполняется возврат на экран списка колод; список отображает
     * те же колоды и счётчики, что и до перехода.
     */
    @Test
    fun systemBack_fromReview_returnsToDeckListWithSameDecksAndCounts() {
        composeRule.onNodeWithText("Базовая колода").performClick()

        composeRule.runOnIdle { navController.popBackStack() }

        composeRule.onNodeWithText("Базовая колода").assertExists()
        composeRule.onNodeWithText("5 карточек").assertExists()
        assertEquals("decks", navController.currentDestination?.route)
    }

    /**
     * S6. GIVEN пользователь полностью завершил сессию и находится
     * на терминальном экране «на сегодня всё».
     * WHEN выполняется системный «назад».
     * THEN выполняется возврат на список колод (S12: счётчик карточек
     * колоды по-прежнему «5 карточек» — оценки не меняют данные).
     */
    @Test
    fun systemBack_fromFinishedScreen_returnsToDeckListWithUnchangedCounts() {
        composeRule.onNodeWithText("Базовая колода").performClick()

        // Полный проход сессии из карточек фейковой колоды: cat → dog.
        listOf("cat", "dog").forEach { front ->
            composeRule.onNodeWithText(front, substring = true).performClick()
            composeRule.onNodeWithText("Хорошо").performClick()
        }
        composeRule.onNodeWithText("На сегодня всё").assertExists()

        composeRule.runOnIdle { navController.popBackStack() }

        composeRule.onNodeWithText("Базовая колода").assertExists()
        composeRule.onNodeWithText("5 карточек").assertExists()
        assertEquals("decks", navController.currentDestination?.route)
    }

    /**
     * S8 (аналог 404). GIVEN на экране списка отображается элемент колоды,
     * данные которой к моменту тапа уже отсутствуют в БД
     * (устаревшее состояние списка: у колоды 2 нет карточек).
     * WHEN пользователь тапает по этому элементу.
     * THEN экран тренировки не открывается на пустом наборе карточек:
     * ReviewViewModel переходит в NotFound и эмитит navigateBack,
     * NavHost выполняет popBackStack — пользователь остаётся на списке,
     * список отрисован из актуального Flow Room; крах приложения исключён.
     */
    @Test
    fun tapOnStaleDeck_staysOnDeckList_withoutCrash() {
        composeRule.onNodeWithText("Дополнительная").performClick()

        composeRule.runOnIdle {
            assertEquals("decks", navController.currentDestination?.route)
        }

        composeRule.onNodeWithText("Базовая колода").assertExists()
        composeRule.onNodeWithText("Дополнительная").assertExists()
        composeRule.onNodeWithText("5 карточек").assertExists()
        assertTrue(repository.cardRequests.contains(2L))
    }
}

/**
 * Фейковый репозиторий для навигационных тестов: фиксированные колоды
 * и карточки, мутаций не выполняет (S12). Колода 2 возвращает пустой
 * список карточек — устаревший элемент (S8).
 */
private class NavigationFakeRepository(
    private val decks: List<Deck>,
) : DeckRepository {

    val cardRequests = mutableListOf<Long>()

    private val cardsByDeck = mapOf(
        1L to listOf(
            ReviewCard(id = 11L, front = "cat", back = "кошка"),
            ReviewCard(id = 12L, front = "dog", back = "собака"),
        ),
        // У колоды 2 карточек в БД нет — данные устарели к моменту тапа (S8).
        2L to emptyList<ReviewCard>(),
    )

    override fun observeDecks(): Flow<Result<List<Deck>>> =
        flowOf(Result.success(decks))

    override fun observeCards(deckId: Long): Flow<Result<List<ReviewCard>>> {
        cardRequests += deckId
        return flowOf(Result.success(cardsByDeck[deckId].orEmpty()))
    }
}
