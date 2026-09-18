package com.mnemocards.ui.deckcontent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.data.db.CardEntity
import com.mnemocards.ui.theme.MnemoCardsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckContentScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun card(id: Long, front: String, back: String) =
        CardEntity(
            id = "c$id",
            deckId = "d1",
            front = front,
            back = back,
            createdAt = id,
        )

    private fun setContent(state: DeckContentUiState) {
        composeRule.setContent {
            MnemoCardsTheme {
                DeckContentScreen(
                    state = state,
                    onBack = {},
                    onAddCard = {},
                    onEditCard = {},
                    onDeleteCard = {},
                    onRetry = {},
                )
            }
        }
    }

    // S1: заголовок — название колоды, каждый элемент показывает обе стороны.
    @Test
    fun content_showsTitleAndBothSides() {
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(
                    card(3, "hello", "привет"),
                    card(2, "world", "мир"),
                    card(1, "cat", "кошка"),
                ),
            ),
        )

        composeRule.onNodeWithText("Английский базовый").assertIsDisplayed()
        composeRule.onNodeWithText("hello — привет").assertIsDisplayed()
        composeRule.onNodeWithText("world — мир").assertIsDisplayed()
        composeRule.onNodeWithText("cat — кошка").assertIsDisplayed()
    }

    // S1: видна кнопка добавления карточки и возврат к списку колод.
    @Test
    fun content_showsAddButtonAndBack() {
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(card(1, "hello", "привет")),
            ),
        )

        composeRule.onNodeWithContentDescription("Добавить карточку").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Назад").assertIsDisplayed()
    }

    // S2: пустая колода — пустое состояние, кнопка добавления доступна.
    @Test
    fun emptyDeck_showsEmptyState() {
        setContent(DeckContentUiState.Empty(title = "Английский базовый"))

        composeRule.onNodeWithText("В колоде пока нет карточек").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Добавить карточку").assertIsDisplayed()
    }

    // S9: дубликат карточки — обе записи отображаются.
    @Test
    fun duplicateCards_bothDisplayed() {
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(
                    card(2, "cat", "кошка"),
                    card(1, "cat", "кошка"),
                ),
            ),
        )

        val nodes = composeRule.onAllNodes(hasText("cat — кошка"))
        composeRule.runOnIdle {
            assertTrue(nodes.fetchSemanticsNodes().size == 2)
        }
    }

    // S10: длинный текст (200+ символов) отображается, макет не ломается.
    @Test
    fun longText_displayed_layoutNotBroken() {
        val longFront = "f".repeat(250)
        val longBack = "б".repeat(250)
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(card(1, longFront, longBack)),
            ),
        )

        composeRule.onNodeWithText("$longFront — $longBack").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Добавить карточку").assertIsDisplayed()
    }

    // S24: колода не найдена — состояние NotFound, а не пустой список.
    @Test
    fun notFound_showsState() {
        setContent(DeckContentUiState.NotFound)

        composeRule.onNodeWithText("Колода не найдена").assertIsDisplayed()
        composeRule.onNodeWithText("К списку колод").assertIsDisplayed()
    }

    // S26: ошибка чтения — состояние ошибки с возможностью повторить.
    @Test
    fun error_showsRetry() {
        setContent(DeckContentUiState.Error)

        composeRule.onNodeWithText("Повторить").assertIsDisplayed()
    }

    // S1: клик по элементу — коллбек редактирования.
    @Test
    fun clickOnCard_invokesEditCallback() {
        var clickedCardId: String? = null
        composeRule.setContent {
            MnemoCardsTheme {
                DeckContentScreen(
                    state = DeckContentUiState.Content(
                        title = "Английский базовый",
                        cards = listOf(card(1, "hello", "привет")),
                    ),
                    onBack = {},
                    onAddCard = {},
                    onEditCard = { clickedCardId = it },
                    onDeleteCard = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("hello — привет").performClick()
        composeRule.runOnIdle {
            assertEquals("c1", clickedCardId)
        }
    }

    // S15: тап по удалению открывает диалог; карточка до выбора остаётся в списке.
    @Test
    fun deleteAction_opensDialog_cardStaysInList() {
        setContent(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(
                    card(2, "world", "мир"),
                    card(1, "hello", "привет"),
                ),
            ),
        )

        composeRule
            .onAllNodes(hasContentDescription("Удалить карточку"))[0]
            .performClick()

        composeRule.onNodeWithText("Удалить карточку?").assertIsDisplayed()
        composeRule.onNodeWithText("Удалить").assertIsDisplayed()
        composeRule.onNodeWithText("Отмена").assertIsDisplayed()
        composeRule.onNodeWithText("hello — привет").assertIsDisplayed()
        composeRule.onNodeWithText("world — мир").assertIsDisplayed()
    }

    // S17: «Отмена» — диалог закрывается, карточка осталась в списке.
    @Test
    fun deleteDialog_cancel_cardStays() {
        var deletedCardId: String? = null
        composeRule.setContent {
            MnemoCardsTheme {
                DeckContentScreen(
                    state = DeckContentUiState.Content(
                        title = "Английский базовый",
                        cards = listOf(card(1, "hello", "привет")),
                    ),
                    onBack = {},
                    onAddCard = {},
                    onEditCard = {},
                    onDeleteCard = { deletedCardId = it },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Удалить карточку").performClick()
        composeRule.onNodeWithText("Отмена").performClick()

        composeRule.onNodeWithText("hello — привет").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(deletedCardId == null)
        }
    }

    // S16: «Удалить» — диалог закрыт, вызван коллбек VM, карточка исчезла из списка.
    @Test
    fun deleteDialog_confirm_invokesCallback_cardRemovedWithoutReload() {
        var deletedCardId: String? = null
        var state by mutableStateOf(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(
                    card(2, "world", "мир"),
                    card(1, "hello", "привет"),
                ),
            ) as DeckContentUiState,
        )
        composeRule.setContent {
            MnemoCardsTheme {
                DeckContentScreen(
                    state = state,
                    onBack = {},
                    onAddCard = {},
                    onEditCard = {},
                    onDeleteCard = { cardId ->
                        deletedCardId = cardId
                        // UI обновляется через Flow из Room — без ручной перезагрузки.
                        val current = state
                        if (current is DeckContentUiState.Content) {
                            val remaining =
                                current.cards.filterNot { it.id == cardId }
                            state = if (remaining.isEmpty()) {
                                DeckContentUiState.Empty(current.title)
                            } else {
                                current.copy(cards = remaining)
                            }
                        }
                    },
                    onRetry = {},
                )
            }
        }

        composeRule
            .onAllNodes(hasContentDescription("Удалить карточку"))[0]
            .performClick()
        composeRule.onNodeWithText("Удалить").performClick()

        composeRule.runOnIdle {
            assertTrue(deletedCardId != null)
        }
        composeRule.onNodeWithText("hello — привет").assertDoesNotExist()
        composeRule.onNodeWithText("world — мир").assertIsDisplayed()
    }

    // S18: удаление последней карточки — список пуст, показано пустое состояние.
    @Test
    fun deleteLastCard_showsEmptyState_appDoesNotCrash() {
        var state by mutableStateOf(
            DeckContentUiState.Content(
                title = "Английский базовый",
                cards = listOf(card(1, "hello", "привет")),
            ) as DeckContentUiState,
        )
        composeRule.setContent {
            MnemoCardsTheme {
                DeckContentScreen(
                    state = state,
                    onBack = {},
                    onAddCard = {},
                    onEditCard = {},
                    onDeleteCard = { cardId ->
                        val current = state
                        if (current is DeckContentUiState.Content) {
                            val remaining =
                                current.cards.filterNot { it.id == cardId }
                            state = if (remaining.isEmpty()) {
                                DeckContentUiState.Empty(current.title)
                            } else {
                                current.copy(cards = remaining)
                            }
                        }
                    },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Удалить карточку").performClick()
        composeRule.onNodeWithText("Удалить").performClick()

        composeRule.onNodeWithText("В колоде пока нет карточек").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Добавить карточку").assertIsDisplayed()
    }
}
