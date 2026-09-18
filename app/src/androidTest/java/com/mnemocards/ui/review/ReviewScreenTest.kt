package com.mnemocards.ui.review

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.CardRepository
import com.mnemocards.domain.model.Card
import com.mnemocards.domain.scheduler.ReviewGrade
import com.mnemocards.ui.theme.MnemoCardsTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val NOW = 1_000_000_000L
private const val DAY = 86_400_000L

/**
 * Compose UI-тесты экрана тренировки (change add-sm2-scheduling):
 * сессия собирается из назревших карточек, переворот показывает
 * кнопки с интервалами, оценка завершает сессию.
 */
@RunWith(AndroidJUnit4::class)
class ReviewScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private class FakeRepository : CardRepository {
        val cards = MutableStateFlow<List<Card>>(emptyList())
        val appliedGrades = mutableListOf<ReviewGrade>()

        override suspend fun dueCards(deckId: String, now: Long): List<Card> = cards.value

        override suspend fun applyGrade(card: Card, grade: ReviewGrade, now: Long) {
            appliedGrades.add(grade)
        }

        override suspend fun deckExists(deckId: String): Boolean = true
    }

    private fun newCard(front: String, back: String) = Card(
        id = "c1",
        deckId = "d1",
        front = front,
        back = back,
        easinessFactor = 2.5,
        repetitionNumber = 0,
        intervalDays = 0,
        dueDate = NOW,
    )

    private fun setContent(repository: FakeRepository, onExit: () -> Unit = {}) {
        composeRule.setContent {
            MnemoCardsTheme {
                ReviewScreen(
                    onBack = {},
                    onExit = onExit,
                    viewModel = ReviewViewModel(
                        deckId = "d1",
                        repository = repository,
                        nowProvider = { NOW },
                    ),
                )
            }
        }
    }

    /** Активная сессия: лицевая сторона, тап переворачивает и показывает интервалы (S14). */
    @Test
    fun activeCard_showsFront_tapRevealsBackAndIntervals() {
        val repository = FakeRepository().apply { cards.value = listOf(newCard("cat", "кошка")) }
        setContent(repository)

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("cat").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("cat").assertIsDisplayed()
        composeRule.onNodeWithText("кошка", substring = true).assertDoesNotExist()

        composeRule.onNodeWithText("cat").performClick()

        composeRule.onNodeWithText("кошка").assertIsDisplayed()
        // S13, S14: подписи интервалов на кнопках до нажатия (новая карточка).
        composeRule.onNodeWithText("10 мин").assertIsDisplayed()
        composeRule.onNodeWithText("1 день").assertExists()
        composeRule.onNodeWithText("4 дня").assertExists()

        // Оценка «Хорошо» применяется и завершает сессию (S2, S20).
        composeRule.onNodeWithText("Хорошо").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("На сегодня всё").fetchSemanticsNodes().isNotEmpty()
        }
        assertTrue(ReviewGrade.GOOD in repository.appliedGrades)
    }

    /** Завершённая сессия: кнопка «К списку колод» вызывает onExit (S17). */
    @Test
    fun finishedScreen_exitButton_invokesOnExit() {
        var exited = false
        setContent(FakeRepository(), onExit = { exited = true })

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("На сегодня всё").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("К списку колод").assertIsDisplayed()

        composeRule.onNodeWithText("К списку колод").performClick()

        composeRule.runOnIdle { assertTrue(exited) }
    }
}
