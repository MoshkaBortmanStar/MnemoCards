package com.mnemocards.ui.review

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.topLeft
import androidx.lifecycle.SavedStateHandle
import com.mnemocards.data.HardcodedDeck
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI-тесты краевых взаимодействий экрана тренировки по сценариям
 * S3, S4, S12 из spec-bdd.md (change `add-review-screen`). Задача T11.
 *
 * Границы интеракций: переворот выполняется только тапом по самой карточке (S3),
 * повторный тап по перевёрнутой карточке состояние не меняет (S4), double-tap
 * по кнопке оценки обрабатывается как ровно одно нажатие (S12).
 *
 * change-002: экран stateless — состояние из [ReviewViewModel]
 * (данные через [FakeReviewRepository]).
 */
class ReviewScreenEdgeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ratingButtonTags = listOf("btnForgot", "btnHard", "btnGood", "btnEasy")

    private lateinit var viewModel: ReviewViewModel

    private fun launchReviewScreen() {
        viewModel = ReviewViewModel(
            savedStateHandle = SavedStateHandle(mapOf("deckId" to 1L)),
            repository = FakeReviewRepository(),
        )
        composeRule.setContent {
            val uiState by viewModel.uiState.collectAsState()
            ReviewScreen(
                uiState = uiState,
                onFlip = viewModel::onFlip,
                onGrade = viewModel::onGrade,
                onBack = viewModel::onBack,
            )
        }
        // Loading с бесконечной анимацией индикатора не даёт Espresso
        // успокоиться — ждём карточку поллингом, а не waitForIdle.
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("reviewCard").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }

    private fun flipCurrentCard() {
        composeRule.onNodeWithTag("reviewCard").performClick()
    }

    private fun assertRatingButtonsHidden() {
        ratingButtonTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    private fun assertRatingButtonsShown() {
        ratingButtonTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    /**
     * S3: GIVEN экран тренировки показывает неперевёрнутую карточку
     * (лицевая сторона).
     * WHEN тап по экрану вне границ карточки (верхний левый угол корня).
     * THEN состояние экрана не меняется: карточка остаётся лицевой стороной,
     * перевод не показывается, кнопки оценки не появляются — переворот
     * выполняется только тапом по самой карточке.
     */
    @Test
    fun s3_tapOutsideCard_doesNotFlipIt() {
        launchReviewScreen()

        composeRule.onRoot().performTouchInput { click(topLeft) }

        composeRule.onNodeWithTag("reviewCard").assertIsDisplayed()
        composeRule.onNodeWithTag("cardSideText", useUnmergedTree = true)
            .assertTextEquals(HardcodedDeck.cards.first().front)
        composeRule.onNodeWithText(HardcodedDeck.cards.first().back)
            .assertDoesNotExist()
        assertRatingButtonsHidden()
    }

    /**
     * S4: GIVEN карточка перевёрнута — показан перевод и четыре кнопки оценки.
     * WHEN повторный тап по карточке.
     * THEN состояние экрана не меняется: карточка остаётся перевёрнутой,
     * кнопки оценки остаются на экране; обратный переворот и переход к
     * следующей карточке не выполняются.
     */
    @Test
    fun s4_tapOnFlippedCard_keepsFlippedStateAndButtons() {
        launchReviewScreen()
        flipCurrentCard()

        flipCurrentCard()

        composeRule.onNodeWithTag("cardSideText", useUnmergedTree = true)
            .assertTextEquals(HardcodedDeck.cards.first().back)
        composeRule.onNodeWithText(HardcodedDeck.cards.first().front)
            .assertDoesNotExist()
        assertRatingButtonsShown()
        // Перехода к следующей карточке нет: показана всё ещё карточка №1.
        composeRule.onNodeWithText(HardcodedDeck.cards[1].front)
            .assertDoesNotExist()
    }

    /**
     * S12: GIVEN карточка №1 перевёрнута, кнопки оценки активны.
     * WHEN двойное быстрое нажатие одной и той же кнопки («Хорошо», doubleClick).
     * THEN обрабатывается только первое нажатие: показана ровно следующая
     * карточка (№2) лицевой стороной, кнопки скрыты; пропуск до карточки №3
     * не происходит, сессия карточку не пропускает.
     */
    @Test
    fun s12_doubleClickOnRatingButton_advancesExactlyOneCard() {
        launchReviewScreen()
        flipCurrentCard()

        composeRule.onNodeWithTag("btnGood").performTouchInput { doubleClick() }

        // Ровно один переход: карточка №2 лицевой стороной.
        composeRule.onNodeWithTag("cardSideText", useUnmergedTree = true)
            .assertTextEquals(HardcodedDeck.cards[1].front)
        composeRule.onNodeWithText(HardcodedDeck.cards[1].back)
            .assertDoesNotExist()
        assertRatingButtonsHidden()
        // Пропуск до карточки №3 (i+2) не произошёл.
        composeRule.onNodeWithText(HardcodedDeck.cards[2].front)
            .assertDoesNotExist()
        composeRule.onNodeWithText(HardcodedDeck.cards[2].back)
            .assertDoesNotExist()
    }
}
