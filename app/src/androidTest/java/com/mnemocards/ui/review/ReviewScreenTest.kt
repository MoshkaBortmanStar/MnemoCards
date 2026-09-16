package com.mnemocards.ui.review

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mnemocards.data.HardcodedDeck
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI-тест основного пользовательского флоу экрана тренировки
 * по сценариям S1, S2, S5, S7 из spec-bdd.md (change `add-review-screen`).
 *
 * Задача T1: красное состояние TDD — экран `ReviewScreen` ещё не реализован
 * (задачи T5–T7); тест адресуется testTag-ам `reviewCard`, `cardSideText`,
 * `btnForgot`, `btnHard`, `btnGood`, `btnEasy`, `doneScreen`.
 */
class ReviewScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ratingButtonTags = listOf("btnForgot", "btnHard", "btnGood", "btnEasy")

    private fun launchReviewScreen() {
        composeRule.setContent {
            ReviewScreen(viewModel = ReviewViewModel())
        }
    }

    private fun flipCurrentCard() {
        composeRule.onNodeWithTag("reviewCard").performClick()
    }

    private fun rateCurrentCard(buttonTag: String) {
        composeRule.onNodeWithTag(buttonTag).performClick()
    }

    private fun assertRatingButtonsHidden() {
        ratingButtonTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertDoesNotExist()
        }
    }

    private fun assertRatingButtonsShownWithExactLabels() {
        composeRule.onNodeWithTag("btnForgot").assertIsDisplayed()
        composeRule.onNodeWithTag("btnHard").assertIsDisplayed()
        composeRule.onNodeWithTag("btnGood").assertIsDisplayed()
        composeRule.onNodeWithTag("btnEasy").assertIsDisplayed()
        composeRule.onNodeWithText("Забыл").assertIsDisplayed()
        composeRule.onNodeWithText("Трудно").assertIsDisplayed()
        composeRule.onNodeWithText("Хорошо").assertIsDisplayed()
        composeRule.onNodeWithText("Легко").assertIsDisplayed()
    }

    /** S1: старт сессии — первая карточка лицевой стороной, перевод и кнопки скрыты. */
    @Test
    fun s1_sessionStarts_withFirstCardFaceUp() {
        launchReviewScreen()

        composeRule.onNodeWithTag("reviewCard").assertIsDisplayed()
        composeRule.onNodeWithTag("cardSideText")
            .assertTextEquals(HardcodedDeck.cards.first().front)
        composeRule.onNodeWithText(HardcodedDeck.cards.first().back)
            .assertDoesNotExist()
        assertRatingButtonsHidden()
    }

    /** S2: одиночный тап по карточке — перевод и четыре активные кнопки оценки. */
    @Test
    fun s2_tapOnCard_showsTranslationAndRatingButtons() {
        launchReviewScreen()

        flipCurrentCard()

        composeRule.onNodeWithTag("cardSideText")
            .assertTextEquals(HardcodedDeck.cards.first().back)
        assertRatingButtonsShownWithExactLabels()
    }

    /** S5: кнопка «Хорошо» — карточка №2 в исходном состоянии (лицевая сторона). */
    @Test
    fun s5_rateGood_showsNextCardFaceUp() {
        launchReviewScreen()
        flipCurrentCard()

        rateCurrentCard("btnGood")

        composeRule.onNodeWithTag("cardSideText")
            .assertTextEquals(HardcodedDeck.cards[1].front)
        composeRule.onNodeWithText(HardcodedDeck.cards[1].back)
            .assertDoesNotExist()
        assertRatingButtonsHidden()
    }

    /** S7: оценка последней (5-й) карточки — экран «на сегодня всё». */
    @Test
    fun s7_ratingLastCard_showsSessionDoneScreen() {
        launchReviewScreen()

        repeat(HardcodedDeck.cards.size) {
            flipCurrentCard()
            rateCurrentCard("btnGood")
        }

        composeRule.onNodeWithTag("doneScreen").assertIsDisplayed()
        composeRule.onNodeWithTag("reviewCard").assertDoesNotExist()
        composeRule.onNodeWithTag("cardSideText").assertDoesNotExist()
        assertRatingButtonsHidden()
    }
}
