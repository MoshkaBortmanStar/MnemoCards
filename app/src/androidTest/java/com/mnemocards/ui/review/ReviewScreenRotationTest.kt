package com.mnemocards.ui.review

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mnemocards.MainActivity
import com.mnemocards.data.HardcodedDeck
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI-тест сценария S9 из spec-bdd.md (change `add-review-screen`).
 * Задача T10: смена конфигурации (поворот экрана) сохраняет состояние сессии.
 *
 * GIVEN идёт сессия, показана перевёрнутая карточка №3 (перевод и кнопки
 * оценки на экране).
 * WHEN происходит смена конфигурации устройства и Activity пересоздаётся
 * (`ActivityScenario.recreate()`).
 * THEN после пересоздания отображается та же карточка №3 в перевёрнутом
 * состоянии с кнопками оценки; прогресс сессии не сброшен, показ карточки №1
 * с начала не происходит.
 *
 * Гарантия — состояние живёт в `ReviewViewModel` (T4) и получается в
 * `MainActivity` через `viewModel()` (T8), а не в `remember`/`saveable`
 * внутри композиции: ViewModelStore переживает пересоздание Activity.
 */
class ReviewScreenRotationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val ratingButtonTags = listOf("btnForgot", "btnHard", "btnGood", "btnEasy")

    private fun flipCurrentCard() {
        composeRule.onNodeWithTag("reviewCard").performClick()
    }

    private fun rateCurrentCardGood() {
        composeRule.onNodeWithTag("btnGood").performClick()
    }

    private fun assertRatingButtonsDisplayed() {
        ratingButtonTags.forEach { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    /** S9: поворот экрана — та же перевёрнутая карточка №3 с кнопками оценки. */
    @Test
    fun s9_configurationChange_keepsFlippedCardAndProgress() {
        // Пройти оценки карточек №1 и №2 — текущей становится карточка №3.
        repeat(THIRD_CARD_INDEX) {
            flipCurrentCard()
            rateCurrentCardGood()
        }

        // GIVEN: карточка №3 перевёрнута — перевод и кнопки оценки на экране.
        flipCurrentCard()
        composeRule.onNodeWithTag("cardSideText")
            .assertTextEquals(HardcodedDeck.cards[THIRD_CARD_INDEX].back)
        composeRule.onNodeWithText(HardcodedDeck.cards[THIRD_CARD_INDEX].front)
            .assertDoesNotExist()
        assertRatingButtonsDisplayed()

        // WHEN: смена конфигурации — Activity пересоздаётся (поворот экрана).
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        // THEN: та же карточка №3 перевёрнута, кнопки оценки на экране,
        // прогресс не сброшен.
        composeRule.onNodeWithTag("reviewCard").assertIsDisplayed()
        composeRule.onNodeWithTag("cardSideText")
            .assertTextEquals(HardcodedDeck.cards[THIRD_CARD_INDEX].back)
        composeRule.onNodeWithText(HardcodedDeck.cards[THIRD_CARD_INDEX].front)
            .assertDoesNotExist()
        assertRatingButtonsDisplayed()
        // Показ карточки №1 с начала не происходит.
        composeRule.onNodeWithText(HardcodedDeck.cards.first().front)
            .assertDoesNotExist()
        composeRule.onNodeWithText(HardcodedDeck.cards.first().back)
            .assertDoesNotExist()
    }

    private companion object {

        /** Индекс карточки №3 (zero-based) в захардкоженной колоде. */
        const val THIRD_CARD_INDEX = 2
    }
}
