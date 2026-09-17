package com.mnemocards.ui.review

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mnemocards.MainActivity
import com.mnemocards.data.db.DatabaseSeeder
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI-тест сценария S9 из spec-bdd.md (change `add-review-screen`).
 * Задача T10: смена конфигурации (поворот экрана) сохраняет состояние сессии.
 *
 * change-002: приложение стартует с экрана списка колод (Room), тренировка
 * открывается тапом по колоде; карточки колоды — [DatabaseSeeder.BASE_CARDS].
 *
 * GIVEN идёт сессия, показана перевёрнутая карточка №3 (перевод и кнопки
 * оценки на экране).
 * WHEN происходит смена конфигурации устройства и Activity пересоздаётся
 * (`ActivityScenario.recreate()`).
 * THEN после пересоздания отображается та же карточка №3 в перевёрнутом
 * состоянии с кнопками оценки; прогресс сессии не сброшен, показ карточки №1
 * с начала не происходит.
 *
 * Гарантия — состояние живёт в `ReviewViewModel` и его `SavedStateHandle`,
 * а не в `remember`/`saveable` внутри композиции: ViewModelStore переживает
 * пересоздание Activity.
 */
class ReviewScreenRotationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /** Колоды Room: пары «слово + перевод» в порядке сидинга. */
    private val baseCards = DatabaseSeeder.BASE_CARDS

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

    /** Открыть экран тренировки базовой колоды со стартового списка. */
    private fun openReviewScreen() {
        // Поллинг вместо idle-ожидания: Loading с бесконечной анимацией
        // индикатора не даёт Espresso успокоиться.
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule
                .onAllNodesWithText(DatabaseSeeder.BASE_DECK_NAME)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText(DatabaseSeeder.BASE_DECK_NAME).performClick()
        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesWithTag("reviewCard").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("reviewCard").assertIsDisplayed()
    }

    /** S9: поворот экрана — та же перевёрнутая карточка №3 с кнопками оценки. */
    @Test
    fun s9_configurationChange_keepsFlippedCardAndProgress() {
        openReviewScreen()

        // Пройти оценки карточек №1 и №2 — текущей становится карточка №3.
        repeat(THIRD_CARD_INDEX) {
            flipCurrentCard()
            rateCurrentCardGood()
        }

        // GIVEN: карточка №3 перевёрнута — перевод и кнопки оценки на экране.
        flipCurrentCard()
        composeRule.onNodeWithTag("cardSideText", useUnmergedTree = true)
            .assertTextEquals(baseCards[THIRD_CARD_INDEX].second)
        composeRule.onNodeWithText(baseCards[THIRD_CARD_INDEX].first)
            .assertDoesNotExist()
        assertRatingButtonsDisplayed()

        // WHEN: смена конфигурации — Activity пересоздаётся (поворот экрана).
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()

        // THEN: та же карточка №3 перевёрнута, кнопки оценки на экране,
        // прогресс не сброшен.
        composeRule.onNodeWithTag("reviewCard").assertIsDisplayed()
        composeRule.onNodeWithTag("cardSideText", useUnmergedTree = true)
            .assertTextEquals(baseCards[THIRD_CARD_INDEX].second)
        composeRule.onNodeWithText(baseCards[THIRD_CARD_INDEX].first)
            .assertDoesNotExist()
        assertRatingButtonsDisplayed()
        // Показ карточки №1 с начала не происходит.
        composeRule.onNodeWithText(baseCards.first().first)
            .assertDoesNotExist()
        composeRule.onNodeWithText(baseCards.first().second)
            .assertDoesNotExist()
    }

    private companion object {

        /** Индекс карточки №3 (zero-based) в базовой колоде Room. */
        const val THIRD_CARD_INDEX = 2
        const val TIMEOUT_MS = 10_000L
    }
}
