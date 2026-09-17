package com.mnemocards.ui.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.ui.theme.MnemoCardsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI-тесты экрана тренировки (API change-003):
 * экран stateless, состояние поднимается наверх через коллбеки.
 */
@RunWith(AndroidJUnit4::class)
class ReviewScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** Активная сессия: видна лицевая сторона; тап по карточке переворачивает её. */
    @Test
    fun activeCard_showsFront_tapRevealsBack() {
        var revealed by mutableStateOf(false)
        composeRule.setContent {
            MnemoCardsTheme {
                ReviewScreen(
                    state = ReviewUiState(
                        front = "cat",
                        back = "кошка",
                        isRevealed = revealed,
                        isFinished = false,
                    ),
                    onFlip = { revealed = true },
                    onRate = {},
                    onBack = {},
                    onExit = {},
                )
            }
        }

        composeRule.onNodeWithText("cat").assertIsDisplayed()
        composeRule.onNodeWithText("кошка", substring = true).assertDoesNotExist()

        composeRule.onNodeWithText("cat").performClick()

        composeRule.onNodeWithText("кошка").assertIsDisplayed()
    }

    /** Завершённая сессия: кнопка «К списку колод» вызывает onExit. */
    @Test
    fun finishedScreen_exitButton_invokesOnExit() {
        var exited = false
        composeRule.setContent {
            MnemoCardsTheme {
                ReviewScreen(
                    state = ReviewUiState(isFinished = true),
                    onFlip = {},
                    onRate = {},
                    onBack = {},
                    onExit = { exited = true },
                )
            }
        }

        composeRule.onNodeWithText("На сегодня всё").assertIsDisplayed()
        composeRule.onNodeWithText("К списку колод").assertIsDisplayed()

        composeRule.onNodeWithText("К списку колод").performClick()

        composeRule.runOnIdle { assertTrue(exited) }
    }
}
