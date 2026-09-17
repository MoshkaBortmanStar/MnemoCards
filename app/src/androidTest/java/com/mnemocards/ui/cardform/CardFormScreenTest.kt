package com.mnemocards.ui.cardform

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mnemocards.domain.CardFormValidator
import com.mnemocards.ui.theme.MnemoCardsTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardFormScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Подключает экран к локальному состоянию; onSave прогоняет реальный
     * доменный валидатор, воспроизводя поведение ViewModel (UDF).
     */
    private fun setContent(initial: CardFormUiState): androidx.compose.runtime.MutableState<CardFormUiState> {
        val state = mutableStateOf(initial)
        composeRule.setContent {
            MnemoCardsTheme {
                CardFormScreen(
                    state = state.value,
                    onFrontChange = { value ->
                        state.value = state.value.copy(front = value, frontError = null)
                    },
                    onBackChange = { value ->
                        state.value = state.value.copy(back = value, backError = null)
                    },
                    onSave = {
                        val errors = CardFormValidator.validateCardInput(
                            state.value.front,
                            state.value.back,
                        )
                        state.value = state.value.copy(
                            frontError = errors.front,
                            backError = errors.back,
                        )
                    },
                    onBack = { state.value = state.value.copy(saveError = null) },
                )
            }
        }
        return state
    }

    // S4: форма добавления — два пустых поля и кнопка «Сохранить».
    @Test
    fun addMode_showsEmptyFieldsAndSaveButton() {
        setContent(CardFormUiState())

        composeRule.onNodeWithText("Лицевая сторона").assertIsDisplayed()
        composeRule.onNodeWithText("Перевод").assertIsDisplayed()
        composeRule.onNodeWithText("Сохранить").assertIsDisplayed()
    }

    // S6: пустое поле при заполненном другом — ошибка у пустого,
    // форма открыта, введённые значения на месте.
    @Test
    fun saveWithEmptyBack_showsError_keepsValues() {
        setContent(CardFormUiState(front = "dog", back = ""))

        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Заполните поле").assertIsDisplayed()
        composeRule.onNodeWithText("dog").assertIsDisplayed()
        composeRule.onNodeWithText("Лицевая сторона").assertIsDisplayed()
    }

    // S7: оба поля пусты — ошибки у обоих.
    @Test
    fun saveWithBothEmpty_showsBothErrors() {
        setContent(CardFormUiState(front = "", back = ""))

        composeRule.onNodeWithText("Сохранить").performClick()

        val errorNodes =
            composeRule.onAllNodes(hasText("Заполните поле"))
        composeRule.runOnIdle {
            assertTrue(errorNodes.fetchSemanticsNodes().size == 2)
        }
    }

    // S8: только пробельные символы — ошибки после trim.
    @Test
    fun saveWithWhitespaceOnly_showsErrors() {
        setContent(CardFormUiState(front = "   ", back = " \n\t "))

        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Заполните поле").assertIsDisplayed()
    }

    // S11: режим редактирования — поля предзаполнены текущими значениями.
    @Test
    fun editMode_fieldsPrefilled() {
        setContent(CardFormUiState(front = "cat", back = "кошка"))

        composeRule.onNodeWithText("cat").assertIsDisplayed()
        composeRule.onNodeWithText("кошка").assertIsDisplayed()
    }

    // S13: очищенное поле при редактировании — ошибка, значения не потеряны.
    @Test
    fun editMode_clearedField_showsError_keepsOtherValue() {
        val state = setContent(CardFormUiState(front = "cat", back = "кошка"))

        composeRule.onNodeWithText("кошка").performTextClearance()
        composeRule.onNodeWithText("Сохранить").performClick()

        composeRule.onNodeWithText("Заполните поле").assertIsDisplayed()
        composeRule.onNodeWithText("cat").assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(state.value.front == "cat")
            assertTrue(state.value.back.isEmpty())
        }
    }

    // S14: системный «Назад» без «Сохранить» — возврат, изменений нет.
    @Test
    fun back_withoutSave_noChanges() {
        var backInvoked = false
        val state = mutableStateOf(CardFormUiState(front = "cat", back = "кошка"))
        composeRule.setContent {
            MnemoCardsTheme {
                CardFormScreen(
                    state = state.value,
                    onFrontChange = { value ->
                        state.value = state.value.copy(front = value, frontError = null)
                    },
                    onBackChange = { value ->
                        state.value = state.value.copy(back = value, backError = null)
                    },
                    onSave = { },
                    onBack = { backInvoked = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Назад").performClick()

        composeRule.runOnIdle {
            assertTrue(backInvoked)
            // Прежние значения не изменены, в Room ничего не пишется.
            assertTrue(state.value.front == "cat")
            assertTrue(state.value.back == "кошка")
        }
    }
}
