package com.mnemocards.ui.decks

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mnemocards.domain.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI-тесты экрана списка колод — `DeckListScreen` (T8, change `add-offline-storage`).
 *
 * Экран тестируется с фейковыми состояниями напрямую (stateless-контракт):
 * состояние и колбэки приходят параметрами, бизнес-логики в Compose нет.
 *
 * Покрывает сценарии spec-bdd:
 * - S3 — в Content отрисованы название колоды и счётчик карточек «N карточек»;
 * - S4 (подготовка) — тап по элементу вызывает onDeckClick с идентификатором колоды;
 * - S7 — в Empty отображается заглушка «Пока нет колод»;
 * - S9 — в Error отображаются сообщение об ошибке и кнопка «Повторить»,
 *   тап по кнопке вызывает retry.
 *
 * Тест пишется до реализации экрана (T9) и до неё падает:
 * `DeckListScreen` ещё не существует.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeckListScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val decks = listOf(
        Deck(id = 1L, name = "Базовая колода", cardCount = 5),
        Deck(id = 2L, name = "Дополнительная", cardCount = 2),
    )

    /**
     * S3. GIVEN экран получает состояние Content со списком колод.
     * WHEN экран отрисовывается.
     * THEN для каждой колоды отображаются её название и счётчик
     * количества карточек.
     */
    @Test
    fun contentState_displaysDeckNamesAndCardCounts() {
        composeRule.setContent {
            DeckListScreen(
                uiState = DeckListUiState.Content(decks),
                onDeckClick = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("Базовая колода").assertExists()
        composeRule.onNodeWithText("5 карточек").assertExists()
        composeRule.onNodeWithText("Дополнительная").assertExists()
        composeRule.onNodeWithText("2 карточки").assertExists()
    }

    /**
     * S4 (подготовка). GIVEN экран получает состояние Content со списком колод.
     * WHEN пользователь тапает по элементу колоды.
     * THEN вызывается onDeckClick с идентификатором именно этой колоды.
     */
    @Test
    fun contentState_deckItemClick_callsOnDeckClickWithDeckId() {
        var clickedDeckId: Long? = null

        composeRule.setContent {
            DeckListScreen(
                uiState = DeckListUiState.Content(decks),
                onDeckClick = { deckId -> clickedDeckId = deckId },
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("Базовая колода").performClick()

        assertEquals(1L, clickedDeckId)
    }

    /**
     * S7. GIVEN БД инициализирована, но не содержит ни одной колоды.
     * WHEN экран получает состояние Empty.
     * THEN отображается заглушка «Пока нет колод» — информативное пустое
     * состояние вместо неинформативного пустого экрана.
     */
    @Test
    fun emptyState_displaysPlaceholder() {
        composeRule.setContent {
            DeckListScreen(
                uiState = DeckListUiState.Empty,
                onDeckClick = {},
                onRetry = {},
            )
        }

        composeRule.onNodeWithText("Пока нет колод").assertExists()
    }

    /**
     * S9. GIVEN чтение/открытие БД завершилось ошибкой.
     * WHEN экран получает состояние Error.
     * THEN отображаются сообщение об ошибке и кнопка «Повторить»;
     * тап по кнопке вызывает действие retry — повторную загрузку.
     */
    @Test
    fun errorState_displaysMessageAndRetryButton_callsRetryOnTap() {
        var retryRequested = false

        composeRule.setContent {
            DeckListScreen(
                uiState = DeckListUiState.Error,
                onDeckClick = {},
                onRetry = { retryRequested = true },
            )
        }

        composeRule.onNodeWithText("Не удалось загрузить колоды").assertExists()

        composeRule.onNodeWithText("Повторить").performClick()

        assertTrue(retryRequested)
    }
}
